package me.shumei.open.oks.kuaipan;

import java.io.IOException;
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
import org.json.JSONException;
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
			//存放Cookies的HashMap
			HashMap<String, String> cookies = new HashMap<String, String>();
			//Jsoup的Response
			Response res;
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
					resultFlag = "true";
					resultStr = "今天已签过到";
				}
				else
				{
					int rewardsize = jsonObj.getInt("rewardsize");//增加的容量
					int points = jsonObj.getJSONObject("status").getInt("points");//增加的积分
					resultFlag = "true";
					resultStr = "签到成功，获得" + rewardsize + "M空间，总积分" + points;
				}
			}
			else
			{
				resultFlag = "false";
				resultStr = "登录失败";
			}
			
		} catch (IOException e) {
			this.resultFlag = "false";
			this.resultStr = "连接超时";
			e.printStackTrace();
		} catch (Exception e) {
			this.resultFlag = "false";
			this.resultStr = "未知错误！";
			e.printStackTrace();
		}
		
		return new String[]{resultFlag, resultStr};
	}
	
	
}
