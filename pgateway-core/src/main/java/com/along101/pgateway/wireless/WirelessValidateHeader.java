package com.along101.pgateway.wireless;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;

import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dianping.cat.Cat;
import com.dianping.cat.message.Transaction;
import com.netflix.config.DynamicBooleanProperty;
import com.netflix.config.DynamicPropertyFactory;
import com.along101.pgateway.common.Constants;
import com.along101.pgateway.common.GateException;
import com.along101.pgateway.context.RequestContext;
import com.along101.pgateway.filters.GateFilter;

public class WirelessValidateHeader extends GateFilter {
	
	private static Logger logger = LoggerFactory.getLogger(WirelessValidateHeader.class);

	private static final String GATE_BODY_DEBUG_DISABLE = "gate.body.debug.disable";
	private static final String GATE_HEADER_DEBUG_DISABLE = "gate.header.debug.disable";
	private static final DynamicBooleanProperty BODY_DEBUG_DISABLED =
	DynamicPropertyFactory.getInstance().getBooleanProperty(GATE_BODY_DEBUG_DISABLE, false);
	private static final DynamicBooleanProperty HEADER_DEBUG_DISABLED =
	DynamicPropertyFactory.getInstance().getBooleanProperty(GATE_HEADER_DEBUG_DISABLE, false);

    private List<String> headers = new ArrayList<String>();
    
    public WirelessValidateHeader(){
    	this.headers = Arrays.<String>asList("X-ALONG-APPID", "X-ALONG-APPVERSION", "X-ALONG-DEVICEID", "X-ALONG-TIMESTAMP");
    }

	@Override
	public boolean shouldFilter() {
		RequestContext ctx = RequestContext.getCurrentContext();
		if (!ctx.sendGateResponse())
			return false;

		if(ctx.getThrowable() != null)
			return false;
		
		if (ctx.getServiceName() != null)
			return false;
		return true;
	}


	@Override
	public String filterType() {
		return "pre";
	}

	@Override
	public int filterOrder() {
		return 10;
	}
	
	private void throwException(int httpStatus, String code, String message) throws GateException {
		HttpServletResponse response = RequestContext.getCurrentContext().getResponse();

		response.setHeader(Constants.HTTP_ERROR_CODE_HEADER, code);
		response.setHeader(Constants.HTTP_ERROR_MESSAGE_HEADER, message);
		GateException gateException = new GateException(code, httpStatus, message);
		RequestContext.getCurrentContext().setThrowable(gateException);
		throw gateException;
	}
	
	private void fomatResponse(int httpStatus, String code, String message){
		RequestContext ctx = RequestContext.getCurrentContext();
		HttpServletResponse response = RequestContext.getCurrentContext().getResponse();

		response.setHeader(Constants.HTTP_ERROR_CODE_HEADER, code);
		response.setHeader(Constants.HTTP_ERROR_MESSAGE_HEADER, message);
		ctx.setResponseStatusCode(httpStatus);
		ctx.addGateResponseHeader("Content-Type", "application/json; charset=utf-8");
        ctx.setSendGateResponse(false);
		ctx.setResponseBody("{\"Message\":\""+message+"\"}");
		StringBuilder sb = new StringBuilder(ctx.getRequest().getRequestURI()+":\n"+message);
		if(HEADER_DEBUG_DISABLED.get()){
			Enumeration<String> headerIt = ctx.getRequest().getHeaderNames();		
			while (headerIt.hasMoreElements()) {
				String name = (String) headerIt.nextElement();
				String value = ctx.getRequest().getHeader(name);
				sb.append("REQUEST:: > " + name + ":" + value+"\n");
			}
		}
		logger.warn(sb.toString());
	}

	@Override
	public Object run() throws GateException {
		Transaction tran = Cat.getProducer().newTransaction("Filter", "ValidateHeader");
		try{

			RequestContext ctx = RequestContext.getCurrentContext();

			for (String header : headers) {
				Enumeration<String> headers = ctx.getRequest().getHeaders(header);
				if (!headers.hasMoreElements()) {
					//throwException(HttpServletResponse.SC_BAD_REQUEST, "GTW-BRQ-NOHEADER", String.format("缺少必要的消息头:%s", header));
					fomatResponse(HttpServletResponse.SC_BAD_REQUEST, "GTW-BRQ-NOHEADER", String.format("缺少必要的消息头:%s", header));
					return null;
				}
			}
			tran.setStatus(Transaction.SUCCESS);
		}catch(Throwable e){
			tran.setStatus(e);
			throw e;
		}finally{
			tran.complete();
		}
		return null;
	}

}
