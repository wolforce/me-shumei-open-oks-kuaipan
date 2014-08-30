package me.shumei.open.oks.kuaipan;

import java.util.HashMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;
import org.jsoup.Connection.Method;
import org.jsoup.Connection.Response;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import android.content.Context;

/**
 * 使签到类继承CommonData，以方便使用一些公共配置信息
 * @author wolforce
 *
 */
public class Signin extends CommonData {
    String resultFlag = "false";
    String resultStr = "未知错误！";
    
    /**
     * <p><b>程序的签到入口</b></p>
     * <p>在签到时，此函数会被《一键签到》调用，调用结束后本函数须返回长度为2的一维String数组。程序根据此数组来判断签到是否成功</p>
     * @param ctx 主程序执行签到的Service的Context，可以用此Context来发送广播
     * @param isAutoSign 当前程序是否处于定时自动签到状态<br />true代表处于定时自动签到，false代表手动打开软件签到<br />一般在定时自动签到状态时，遇到验证码需要自动跳过
     * @param cfg “配置”栏内输入的数据
     * @param user 用户名
     * @param pwd 解密后的明文密码
     * @return 长度为2的一维String数组<br />String[0]的取值范围限定为两个："true"和"false"，前者表示签到成功，后者表示签到失败<br />String[1]表示返回的成功或出错信息
     */
    public String[] start(Context ctx, boolean isAutoSign, String cfg, String user, String pwd) {
        //把主程序的Context传送给验证码操作类，此语句在显示验证码前必须至少调用一次
        CaptchaUtil.context = ctx;
        //标识当前的程序是否处于自动签到状态，只有执行此操作才能在定时自动签到时跳过验证码
        CaptchaUtil.isAutoSign = isAutoSign;
        
        try{
            String[] qiandaoResultArr = null;
            String[] yaojiangResultArr = null;
            
            for (int i = 0; i < RETRY_TIMES; i++) {
                qiandaoResultArr = qianDao(user, pwd);
                if (qiandaoResultArr[0].equals("true")) break;
            }
            
            for (int i = 0; i < RETRY_TIMES; i++) {
                yaojiangResultArr = yaoJiang(user, pwd);
                if (yaojiangResultArr[0].equals("true")) break;
            }
            
            //只有签到和摇奖同时成功，整个任务才能标识为成功
            if (qiandaoResultArr[0].equals("true") && yaojiangResultArr[0].equals("true")) resultFlag = "true";
            resultStr = "签到：\n" + qiandaoResultArr[1] + "\n\n摇奖：\n" + yaojiangResultArr[1];
        } catch (Exception e) {
            this.resultFlag = "false";
            this.resultStr = "未知错误！";
            e.printStackTrace();
        }
        
        return new String[]{resultFlag, resultStr};
    }
    
    /**
     * 执行“每日签到”功能，返回拥有两个元素的String数组，String[0]=>成功或失败，String[1]=>详细信息
     * @param user 用户名
     * @param pwd 明文密码
     * @return 返回拥有两个元素的String数组，String[0]=>成功或失败，String[1]=>详细信息
     */
    private String[] qianDao(String user, String pwd) {
        String returnFlag = "false";
        String returnStr = "签到失败";
        try {
            //Jsoup的Document
            Document doc;
            
            String tokenUrl = "http://api-filesys.wps.cn/xsvr/login/";//获取token的链接
            String signUrl = "http://point.wps.cn/kpoints/submit/sign/";//提交签到数据的链接
            
            //声明HttpClient并设置超时时间
            HttpParams httpParams = new BasicHttpParams();
            HttpConnectionParams.setConnectionTimeout(httpParams, TIME_OUT);
            HttpClient hc = new DefaultHttpClient(httpParams);
            
            //构造登录所用的XML数据
            StringBuffer xmlData = new StringBuffer();
            xmlData.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?><xLive><user>");
            xmlData.append(user);
            xmlData.append("</user><password>");
            xmlData.append(pwd);
            xmlData.append("</password><deviceId>MiOne-XiaoMi-wolforce</deviceId><clientName>Android</clientName><clientVersion>2.3</clientVersion></xLive>");
            
            //通过Stream的方式发送XML数据，这个用Jsoup无法完成，改用HttpClient
            HttpPost httpPost = new HttpPost(tokenUrl);
            httpPost.setHeader("Content-Type", "text/xml;charset=utf-8");
            httpPost.setHeader("v", "2");
            httpPost.setEntity(new StringEntity(xmlData.toString()));
            HttpResponse httpResponse = hc.execute(httpPost);
            HttpEntity entity = httpResponse.getEntity();
            String body = EntityUtils.toString(entity);
            if (entity != null)
                entity.consumeContent();
            
            
            //用Jsoup提取返回的userId和token(用Jsoup比较方便，懒得写正则了-_-)
            doc = Jsoup.parse(body);
            String userId = doc.getElementsByTag("userId").text();
            String token = doc.getElementsByTag("token").text();
            
            //如果获取到的userId长度大于0，那就说明登录成功了，否则就没成功
            if(userId.length() > 0)
            {
                //构造签到XML数据
                xmlData.delete(0, xmlData.length());
                xmlData.append("<?xml version=\"1.0\" encoding=\"utf-8\" ?><xLive><token>");
                xmlData.append(token);
                xmlData.append("</token><userId>");
                xmlData.append(userId);
                xmlData.append("</userId></xLive>");
                
                //通过Stream的方式发送XML数据，这个用Jsoup无法完成，改用HttpClient
                //{"status": {"grade": 0, "maxsize": 100, "points": 27, "period": 0}, "rewardsize": 34, "monthtask": {"M900": 18, "invite": 0, "maxnormal": 900, "normal": 18}, "todaytask": {"count": {"sign": 1}}, "userid": "12429213", "increase": 9, "state": 1}
                //{"state": -102}
                httpPost = new HttpPost(signUrl);
                httpPost.setHeader("Content-Type", "text/xml;charset=utf-8");
                httpPost.setHeader("v", "2");
                httpPost.setEntity(new StringEntity(xmlData.toString()));
                httpResponse = hc.execute(httpPost);
                entity = httpResponse.getEntity();
                body = EntityUtils.toString(entity);
                if (entity != null)
                    entity.consumeContent();
                
                JSONObject jsonObj = new JSONObject(body);
                int state = jsonObj.getInt("state");
                if(state == -102)
                {
                    returnFlag = "true";
                    returnStr = "今天已签过到";
                }
                else
                {
                    int rewardsize = jsonObj.getInt("rewardsize");//增加的容量
                    int points = jsonObj.getJSONObject("status").getInt("points");//增加的积分
                    returnFlag = "true";
                    returnStr = "签到成功，获得" + rewardsize + "M空间，总积分" + points;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{returnFlag, returnStr};
    }
    
    /**
     * 执行摇奖功能，返回拥有两个元素的String数组，String[0]=>成功或失败，String[1]=>详细信息
     * @param user 明文用户名
     * @param pwd 明文密码
     * @return 返回拥有两个元素的String数组，String[0]=>成功或失败，String[1]=>详细信息
     */
    private String[] yaoJiang(String user, String pwd) {
        String returnFlag = "false";
        String returnStr = "摇奖失败";
        try {
            //Jsoup的Response
            Response res;
            
            //存放Cookies的HashMap
            HashMap<String, String> cookies = new HashMap<String, String>();
            
            String loginUrl = "http://www.kuaipan.cn/accounts/login/";//提交账号登录信息的链接
            String lotteryUrl = "http://www.kuaipan.cn/turnplate/lottery/";//摇奖的链接
            
            //提交登录信息，如果登录失败就直接返回
            //{"status":"ok","data":""}
            //{"status":"accountNotMatch","data":""}
            res = Jsoup.connect(loginUrl).data("username", user).data("password", pwd).userAgent(UA_CHROME).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
            cookies.putAll(res.cookies());
            JSONObject jsonObj = new JSONObject(res.body());
            String status = jsonObj.optString("status");
            if (!status.equals("ok")) {
                if (status.equals("accountNotMatch")) returnStr = "账号密码不正确";
                return new String[]{returnFlag, returnStr};
            }
            
            //执行到这一步的话，登录一定是成功的，开始摇奖
            //{"status":"ok","data":{"awardType":"500M","channel":"login","ext":{"star":4,"beyond":91,"text":"恭喜哦，您已经甩掉一溜的人排在前面啦，天天至少抽到500m，积少成多就是个大奖啦~"}}}
            res = Jsoup.connect(lotteryUrl).userAgent(UA_CHROME).cookies(cookies).timeout(TIME_OUT).ignoreContentType(true).method(Method.POST).execute();
            cookies.putAll(res.cookies());
            System.out.println(res.body());
            jsonObj = new JSONObject(res.body());
            status = jsonObj.optString("status");
            
            if (status.equals("ok")) {
                JSONObject dataObj = jsonObj.optJSONObject("data");
                String awardType = dataObj.optString("awardType");
                returnFlag = "true";
                returnStr = getDescriptionOfLotteryType(awardType);
            } else if (status.equals("noChance")) {
                returnFlag = "true";
                returnStr = "今天的运气用光啦，明天再来呦～";
            } else {
                returnFlag = "false";
                returnStr = "额，大转盘生病了呢,暂时不能抽奖了哦，过会儿再来吧。么么哒^_^";
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return new String[]{returnFlag, returnStr};
    }
    
    /**
     * 根据返回的奖品类型获取其具体的描述
     * @param lotteryType 奖品的类型
     * @return 返回具体的描述
     */
    private String getDescriptionOfLotteryType(String lotteryType) {
        HashMap<String, String> lotteryList = new HashMap<String, String>();
        lotteryList.put("2G", "2G永久空间");
        lotteryList.put("3G", "3G永久空间");
        lotteryList.put("30G", "30G永久空间");
        lotteryList.put("1T", "1T永久空间");
        lotteryList.put("empty", "继续攒人品");
        lotteryList.put("30M", "30M永久空间");
        lotteryList.put("100M", "100M永久空间");
        lotteryList.put("200M", "200M永久空间");
        lotteryList.put("500M", "500M永久空间");
        lotteryList.put("1G", "1G永久空间");
        
        String description = "无法获取奖品类型";
        if (lotteryList.containsKey(lotteryType)) description = lotteryList.get(lotteryType);
        return description;
    }
    
}
