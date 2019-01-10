package com.ailk.ecaop.biz.product;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.core.tag.EcRocTag;
import org.n3r.core.util.ParamsAppliable;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.processor.BaseAopProcessor;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;

@EcRocTag("broadProductQryN6")
public class BroadProductQueryN6 extends BaseAopProcessor implements ParamsAppliable {

    // 添加省份编码91,因另一个无法下发servieceClassCode
    private static final String[] PARAM_ARRAY = { "ecaop.trade.n6.checkUserParametersMapping.91",
            "ecaop.trade.cbpq.BroadProductQryParametersMapping" };
    private final ParametersMappingProcessor[] PMP = new ParametersMappingProcessor[PARAM_ARRAY.length];

    @Override
    public void process(Exchange exchange) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        Map msg = (Map) body.get("msg");

        // 调用三户接口
        Map threePartMap = MapUtils.asMap("serviceClassCode", "0040", "tradeTypeCode", "0127", "getMode",
                "101001101010001001000000000000", "serialNumber", msg.get("serialNumber"));
        MapUtils.arrayPut(threePartMap, msg, MagicNumber.COPYARRAY);
        Exchange threePartExchage = ExchangeUtils.ofCopy(exchange, threePartMap);
        LanUtils lan = new LanUtils();
        lan.preData(PMP[0], threePartExchage);
        CallEngine.wsCall(threePartExchage, "ecaop.comm.conf.url.UsrForNorthSer" + "." + msg.get("province"));
        lan.xml2Json("ecaop.trades.cbss.threePart.template", threePartExchage);

        Map qruUserProductInfo = threePartExchage.getOut().getBody(Map.class);
        Map brdQryMsg = preData4ProductChg(msg, qruUserProductInfo);
        body.put("msg", brdQryMsg);
        exchange.getIn().setBody(body);
        Map brdQryResult = callBroadProductQry(exchange);
        Map response = dealReturn(brdQryResult, qruUserProductInfo, msg.get("province"));
        exchange.getOut().setBody(response);
    }

    public Map dealReturn(Map brdQryResult, Map threePartInfo, Object province) throws Exception {
        Map returnMap = new HashMap();
        // 可变更产品信息
        returnMap.put("chgProductInfo", dealChgProduct((List<Map>) brdQryResult.get("productInfo")));

        // 用户当前活动信息
        returnMap.put("userMktCamp", dealUserMktCamp(threePartInfo, province));

        // 用户当前使用产品信息处理
        returnMap.put("productInfo", dealOrgProduct(threePartInfo));
        return returnMap;
    }

    public List<Map> dealOrgProduct(Map threePartInfo) {
        List<Map> productInfoListRsp = new ArrayList<Map>();
        Map orgProductInfoRsp = new HashMap();
        Map userInfo = ((List<Map>) threePartInfo.get("userInfo")).get(0);
        orgProductInfoRsp.put("productId", userInfo.get("productId"));
        orgProductInfoRsp.put("productName", userInfo.get("productName"));
        orgProductInfoRsp.put("productDesc", userInfo.get("productName"));

        // 当前产品下主资费信息处理
        List<Map> orgDistInfoListRsp = dealOrgDiscntInfo(threePartInfo);
        orgProductInfoRsp.put("discntInfo", orgDistInfoListRsp);

        productInfoListRsp.add(orgProductInfoRsp);
        return productInfoListRsp;
    }

    public List<Map> dealOrgDiscntInfo(Map threePartInfo) {
        Map orgDistInfoRsp = new HashMap();
        List<Map> orgDistInfoListRsp = new ArrayList<Map>();
        Map userInfo = ((List<Map>) threePartInfo.get("userInfo")).get(0);
        String discntCode = "";
        String discntDesc = "";
        String discntName = "";
        for (Map discntInfo : (List<Map>) userInfo.get("discntInfo")) {
            if (!userInfo.get("productId").equals(discntInfo.get("productId"))) {
                continue;
            }
            discntCode = discntInfo.get("discntCode").toString();
            discntDesc = discntCode;
            discntName = discntCode;
        }
        orgDistInfoRsp.put("discntCode", discntCode);
        orgDistInfoRsp.put("discntName", discntName);
        orgDistInfoRsp.put("discntDesc", discntDesc);
        orgDistInfoRsp.put("brandNumber", threePartInfo.get("brandNumber"));
        orgDistInfoListRsp.add(orgDistInfoRsp);
        return orgDistInfoListRsp;
    }

    public List<Map> dealChgProduct(List<Map> productInfo) {
        if (IsEmptyUtils.isEmpty(productInfo)) {
            return productInfo;
        }
        for (Map prod : productInfo) {
            List<Map> chgDiscntInfo = (List<Map>) prod.get("chgDiscntInfo");
            if (IsEmptyUtils.isEmpty(chgDiscntInfo)) {
                continue;
            }
            for (Map dis : chgDiscntInfo) {
                if (null == dis.get("discntDesc") || "".equals(dis.get("discntDesc"))) {
                    dis.put("discntDesc", dis.get("discntName"));
                }
                dis.put("brandSpeed", new ChangeCodeUtils().changeSpeed(dis.get("brandSpeed")));
                Object startEnable = dis.get("startEnable");
                dis.put("startEnable", "0".equals(startEnable) ? "1" : "1".equals(startEnable) ? "2" : "0");
            }
        }
        return productInfo;
    }

    public Map preData4ProductChg(Map orgMsg, Map threePartInfo) {
        Map userInfo = ((List<Map>) threePartInfo.get("userInfo")).get(0);
        // 为调用可变更产品查询接口准备参数
        Object userId = userInfo.get("userId");
        Object serialNumber = orgMsg.get("serialNumber");
        List<Map> discntInfoList = new ArrayList<Map>();
        for (Map discntInfo : (List<Map>) userInfo.get("discntInfo")) {
            // 主产品id对应的资费
            if (userInfo.get("productId").equals((discntInfo.get("productId")))) {
                discntInfoList.add(discntInfo);
            }
        }
        Map brdQryMsg = new HashMap(orgMsg);
        brdQryMsg.put("userId", userId);
        brdQryMsg.put("serialNumber", serialNumber);
        brdQryMsg.put("productId", userInfo.get("productId"));
        brdQryMsg.put("discntInfo", discntInfoList);

        return brdQryMsg;
    }

    public Map callBroadProductQry(Exchange exchange) throws Exception {
        LanUtils lan = new LanUtils();
        lan.preData(PMP[1], exchange);
        CallEngine.wsCall(exchange, "ecaop.comm.conf.url.osn.services.BroadbandUsrSer");
        lan.xml2Json("ecaop.trade.cbpq.template", exchange);
        return exchange.getOut().getBody(Map.class);
    }

    public List<Map> dealUserMktCamp(Map threePartInfo, Object province) throws Exception {
        List<Map> retList = new ArrayList<Map>();
        List<Map> paraList = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraList)) {
            throw new EcAopServerBizException("9999", "北六ESS未返回PARA信息");
        }
        Object prodCycle = "";// 当前主产品周期
        Object prodTariffCode = "";// 当前主产品资费编码
        Object prodTariffDescribe = "";// 当前主产品资费描述
        for (Map para : paraList) {
            if ("PROD_CYCLE".equals(para.get("paraId"))) {
                prodCycle = para.get("paraValue");
                threePartInfo.put("brandNumber", prodCycle);
            }
            else if ("PROD_TARIFF_CODE".equals(para.get("paraId"))) {
                prodTariffCode = para.get("paraValue");
            }
            else if ("PROD_TARIFF_DESCRIBE".equals(para.get("paraId"))) {
                prodTariffDescribe = para.get("paraValue");
            }
        }
        List<Map> userInfoList = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfoList)) {
            throw new EcAopServerBizException("9999", "省份未返回USER_INFO信息");
        }
        for (Map user : userInfoList) {
            List<Map> discntInfo = (List<Map>) user.get("discntInfo");
            if (IsEmptyUtils.isEmpty(discntInfo)) {
                continue;
            }
            for (Map discnt : discntInfo) {
                if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                    continue;
                }
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                Map temp = new HashMap();
                temp.put("mktCampId", prodTariffCode);
                temp.put("mktCampName", prodTariffDescribe);
                List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
                if (!IsEmptyUtils.isEmpty(attrInfo)) {
                    for (Map attr : attrInfo) {
                        if ("fixedHire".equals(attr.get("attrCode"))) {
                            prodCycle = attr.get("attrValue");
                        }
                    }
                }
                String startDate = discnt.get("startDate").toString();
                String substring = startDate.substring(6, 8);
                temp.put("startDate", startDate);
                String endDate = sdf
                        .format(RDate.addMonths(sdf.parse(startDate), Integer.valueOf(prodCycle.toString())));
                endDate = endDate.substring(0, 8) + "000000";
                endDate = DateUtils.addSeconds(endDate, -1);
                /**
                 * ECS界面展示当前包年产品有效期至**的逻辑：对于月中生效的资费，AOP计算失效时间时应该多加一个月。
                 * （即：开户订购包年资费，生效时间为竣工日，但计算包年时长时开户当月不计算在内）
                    比如包1年资费：
                （1）2017年1月15日生效的话、失效时间应为2018年1月31日
                （2）2017年1月1日00:00:00生效，失效时间应为2017年12月31日。
                 */
                if ("18".equals(province)) {
                    if (substring.equals("01")) {
                        endDate = GetDateUtils.getMonthLastDayFormat1(endDate);
                    }
                    else {
                        String newStartDate = GetDateUtils.getNextDiscntDate(startDate);
                        endDate = sdf
                                .format(RDate.addMonths(sdf.parse(newStartDate), Integer.valueOf(prodCycle.toString())));
                        endDate = endDate.substring(0, 8) + "000000";
                        endDate = DateUtils.addSeconds(endDate, -1);
                        endDate = GetDateUtils.getMonthLastDayFormat1(endDate);
                    }
                }
                temp.put("endDate", endDate);
                retList.add(temp);
            }
        }
        return retList;
    }

    @Override
    public void applyParams(String[] params) {
        for (int i = 0; i < PARAM_ARRAY.length; i++) {
            PMP[i] = new ParametersMappingProcessor();
            PMP[i].applyParams(new String[] { PARAM_ARRAY[i] });
        }
    }
}
