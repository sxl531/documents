package com.ailk.ecaop.biz.cust;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.config.Config;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.AopCall;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;
import org.n3r.ecaop.core.processor.TransReqParamsMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.utils.CertTypeChangeUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;

/**
 * 客户资料创建支持北六
 * @author YangZG
 */
@EcRocTag("N6CustInfoCreateCheck")
@SuppressWarnings(value = { "unchecked", "rawtypes" })
public class N6CustInfoCreateCheckProcessor extends BaseAopProcessor implements ParamsAppliable {

    LanUtils lan = new LanUtils();
    private static final String[] PARAM_ARRAY = { "ecaop.cust.ccic.ParametersMapping",
            "ecaop.trades.ccic.ParametersMapping", "ecaop.trades.seqid.ParametersMapping" };
    private final ParametersMappingProcessor[] pmp = new ParametersMappingProcessor[3];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map<String, Object> inBody = exchange.getIn().getBody(Map.class);
        Map msg = (Map) inBody.get("msg");
        String eparchyCode = (String) msg.get("city");
        certCheck(exchange, inBody);// 证件类型为二代证和户口本的时候，需要调用国政通接口做实名校验，通过后才能创建客户
        // 获取北六TRADE_ID
        String tradeId;
        try {
            tradeId = GetSeqUtil.getSeqFromN6ess(pmp[2], exchange, "TRADE_ID", eparchyCode);
            msg.put("tradeId", tradeId);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "获取CB侧流水失败" + e.getMessage());
        }
        ELKAopLogger.logStr("tradeId=" + tradeId);
        msg.put("orderId", tradeId);
        msg.put("certType", CertTypeChangeUtils.certTypeFbs2Cbss((String) msg.get("certType")));
        inBody.put("msg", msg);
        exchange.getIn().setBody(inBody);
        new TransReqParamsMappingProcessor().process(exchange);
        try {
            lan.preData(pmp[1], exchange);
            CallEngine.wsCall(exchange, "ecaop.comm.conf.url.OSN.services.crtCustInfo");
            lan.xml2Json("ecaop.trades.ccic.crtCustInfo.OSN.template", exchange);
        }
        catch (Exception e) {
            throw new EcAopServerBizException("9999", "调用全业务客户资料创建接口失败！" + e.getMessage());
        }
        delReturn(exchange);
    }

    /**
     * 证件类型为二代证和户口本的时候，需要调用国政通接口做实名校验，通过后才能创建客户 省份开关，前期对河南76开放； 根据证件编码获得证件名称 01:15位身份证 , 02:18位身份证, 03:驾驶证, 04:军官证 ,
     * 05:教师证 , 06:学生证 , 07:营业执照 , 08:护照, 09:武警身份证
     * , 10:港澳居民来往内地通行证 , 11:台湾居民来往大陆通行证 , 12:户口本 , 13:组织机构代码证, 14:事业单位法人证书 ,15:介绍信,20:小微企业客户证件,21:民办非企业单位登记证书,99:其它
     */
    private void certCheck(Exchange exchange, Map<String, Object> tempBody) throws Exception {
        Map<String, Object> inBody = exchange.getIn().getBody(Map.class);
        boolean isString = inBody.get("msg") instanceof String;
        Map msg = new HashMap();
        if (isString) {
            msg = JSON.parseObject((String) inBody.get("msg"));
        }
        else {
            msg = (Map) inBody.get("msg");
        }
        String methodCode = exchange.getMethodCode();
        Map custInfo = null;
        boolean isList = msg.get("custInfo") instanceof List;
        if (isList) {
            if (((List) msg.get("custInfo")).size() != 1) {
                throw new EcAopServerBizException("8888", "客户信息custInfo有且只能有一个");
            }
            custInfo = (Map) ((List) msg.get("custInfo")).get(0);
        }
        else {
            custInfo = (Map) msg.get("custInfo");
        }
        String certType = custInfo.get("certType") + "";
        String province = msg.get("province") + "";
        String provinceConfig = Config.getStr("ecaop.ccic.params.config.province");
        String certNum = (String) custInfo.get("certNum");
        if (provinceConfig.contains(province) && "12".equals(certType)) {
            if (certNum.length() != 18 && certNum.length() != 15) {
                throw new EcAopServerBizException("8888", "证件号码应为15或18位身份证。");
            }
        }
        if (isList) {
            List list = new ArrayList();
            list.add(custInfo);
            msg.put("custInfo", list);
        }
        else {
            msg.put("custInfo", custInfo);
        }
        tempBody.put("msg", msg);
        if (provinceConfig.contains(province) && "01|02|12".contains(certType)) {
            Object customerName = custInfo.get("customerName");
            Object city = msg.get("city");
            Map map = Maps.newHashMap();
            map.put("msg", MapUtils.asMap("province", province, "city", city, "certName", customerName, "certId",
                    certNum, "certType", "01"));
            exchange.getIn().setBody(map);
            exchange.setMethodCode("crck");
            CertCheckTypeProcessor chkProc = new CertCheckTypeProcessor();
            chkProc.process(exchange);
            AopCall call = new AopCall();
            lan.preData(pmp[0], exchange);
            call.applyParams(new String[] { "ecaop.comm.conf.url.phw-eop", "60" });
            call.process(exchange);
            FormatDateAndDealException4Gzt deal = new FormatDateAndDealException4Gzt();
            deal.process(exchange);
            exchange.setMethodCode(methodCode);
        }
    }

    /**
     * 处理返回
     */
    public void delReturn(Exchange exchange) throws Exception {
        Map retMap = new HashMap();
        Map bodyMap = new HashMap();
        Object body = exchange.getOut().getBody();
        if (body instanceof String) {
            bodyMap = JSON.parseObject((String) body);
        }
        else {
            bodyMap = exchange.getOut().getBody(Map.class);
        }
        List<Map> respInfoList = (List<Map>) bodyMap.get("respInfo");
        if (null != respInfoList && !respInfoList.isEmpty()) {

            String custId = (String) respInfoList.get(0).get("custId");
            Map respInfo = new HashMap();
            respInfo.put("custId", custId);
            retMap.put("respInfo", respInfo);
            exchange.getOut().setBody(retMap);
        }
        else {
            exchange.getOut().setBody(bodyMap);
        }
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            pmp[i] = new ParametersMappingProcessor();
            pmp[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }

}
