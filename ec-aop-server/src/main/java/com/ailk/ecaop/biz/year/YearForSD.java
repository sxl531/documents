package com.ailk.ecaop.biz.year;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.n3r.core.lang.RDate;
import org.n3r.ecaop.core.Exchange;
import org.n3r.ecaop.core.Message;
import org.n3r.ecaop.core.exception.biz.EcAopServerBizException;
import org.n3r.ecaop.core.impl.DefaultMessage;
import org.n3r.ecaop.core.log.elk.ELKAopLogger;
import org.n3r.ecaop.core.processor.ParametersMappingProcessor;

import com.ailk.ecaop.base.CallEngine;
import com.ailk.ecaop.biz.product.DateUtils;
import com.ailk.ecaop.common.helper.MagicNumber;
import com.ailk.ecaop.common.utils.ChangeCodeUtils;
import com.ailk.ecaop.common.utils.ExchangeUtils;
import com.ailk.ecaop.common.utils.GetDateUtils;
import com.ailk.ecaop.common.utils.GetSeqUtil;
import com.ailk.ecaop.common.utils.IsEmptyUtils;
import com.ailk.ecaop.common.utils.LanUtils;
import com.ailk.ecaop.common.utils.MapUtils;
import com.ailk.ecaop.common.utils.SuniTradeUtils;
import com.ailk.ecaop.common.utils.TradeManagerUtils;
import com.ailk.ecaop.common.utils.YearPayN6Utils;

public class YearForSD {

    protected ParametersMappingProcessor[] pmp = null;

    public YearForSD(ParametersMappingProcessor[] pmp) {
        this.pmp = pmp;
    }

    private final LanUtils lan = new LanUtils();
    private String apptx;
    private final String ERROR_CODE = "8888";

    public void process(Exchange exchange, Map msg, Map threePartInfo) throws Exception {
        Map body = exchange.getIn().getBody(Map.class);
        apptx = (String) body.get("apptx");
        boolean flag = true;
        if ("1".equals(msg.get("changeTag")) || IsEmptyUtils.isEmpty(msg.get("changeTag"))) {
            flag = false;
            // 虚拟用户预提交
            Map mixMap = preMixNumberInfo(exchange, msg, flag);
            // 成员用户预提交
            Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
            // 正式提交
            orderSub(mixMap, phoneMap, exchange, msg);
        }
        else if ("2".equals(msg.get("changeTag"))) {
            // 虚拟用户预提交
            Map mixMap = preMixNumberInfo(exchange, msg, flag);
            // 成员用户预提交
            Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
            // 正式提交接口
            orderChgSub(exchange, mixMap, phoneMap, msg);
        }
        else if ("3".equals(msg.get("changeTag"))) {
            // 虚拟用户预提交
            Map mixMap = preMixNumberInfo(exchange, msg, flag);
            // 成员用户预提交
            Map phoneMap = prePhoneNumberInfo(exchange, msg, threePartInfo, flag);
            // 正式提交接口
            orderChgSub(exchange, mixMap, phoneMap, msg);
        }
        else {
            throw new EcAopServerBizException(ERROR_CODE, "产品变更方式[" + msg.get("changeTag") + "]不在[1.趸交;2.变更产品;3.变更非主产品");
        }
    }

    private void orderSub(Map mixMap, Map phoneMap, Exchange exchange, Map msg) throws Exception {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        lan.preData(pmp[4], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
            lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        if ("bymc".equals(msg.get("tradeMethod"))) {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("feeInfo", msg.get("retFee"));// 预提交返回的费用
            outMap.put("arrearageMess", msg.get("arrearageMess"));
            outMap.put("totalFee", String.valueOf(submitMap.get("origTotalFee")));
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            outMap.put("custName", msg.get("custName"));
            outMap.put("productType", "1");
            outMap.put("productName", msg.get("productName"));
            outMap.put("discntName", msg.get("discntName"));
            outMap.put("startDate", msg.get("yyStartDate"));
            outMap.put("endDate", msg.get("yyEndDate"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
        else {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("orderNo", orderNo);
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    private void orderChgSub(Exchange exchange, Map mixMap, Map phoneMap, Map msg) {
        Map submitMap = new HashMap();
        MapUtils.arrayPut(submitMap, msg, MagicNumber.COPYARRAY);
        Object orderNo = msg.get("orderNo");
        submitMap.put("orderNo", orderNo);
        dealSubmit(submitMap, mixMap, phoneMap, msg);
        List<Map> payList = new ArrayList<Map>();
        Map pay = new HashMap();
        if (!IsEmptyUtils.isEmpty((List<Map>) msg.get("payInfo"))) {
            List<Map> payInfoList = (List<Map>) msg.get("payInfo");
            for (Map payInfo : payInfoList) {
                pay.put("payType", ChangeCodeUtils.changePayType4N6odsb(payInfo.get("payType")));
                pay.put("payMoney", submitMap.get("origTotalFee"));
                pay.put("subProvinceOrderId", msg.get("provOrderId"));
            }
            payList.add(pay);
            submitMap.put("payInfo", payList);
        }
        Exchange submitExchange = ExchangeUtils.ofCopy(exchange, submitMap);
        LanUtils lan = new LanUtils();
        lan.preData(pmp[4], submitExchange);
        try {
            CallEngine.wsCall(submitExchange, "ecaop.comm.conf.url.osn.services.ordser");
            lan.xml2Json("ecaop.masb.odsb.template", submitExchange);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
        if ("bymc".equals(msg.get("tradeMethod"))) {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("feeInfo", msg.get("retFee"));// 预提交返回的费用
            outMap.put("arrearageMess", msg.get("arrearageMess"));
            outMap.put("totalFee", String.valueOf(submitMap.get("origTotalFee")));
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            outMap.put("custName", msg.get("custName"));
            outMap.put("productType", "1");
            outMap.put("productName", msg.get("productName"));
            outMap.put("discntName", msg.get("prodTariffDesc"));
            outMap.put("startDate", phoneMap.get("startDate"));
            outMap.put("endDate", phoneMap.get("endDate"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
        else {
            Map outMap = new HashMap();
            outMap.put("code", "0000");
            outMap.put("detail", "OK");
            outMap.put("orderNo", orderNo);
            outMap.put("provOrderId", msg.get("provOrderId"));
            outMap.put("provOrderId2", msg.get("provOrderId2"));
            Message out = new DefaultMessage();
            out.setBody(outMap);
            exchange.setOut(out);
        }
    }

    /**
     * 整理正式提交的參數
     */
    private Map dealSubmit(Map submitMap, Map mixMap, Map phoneMap, Map msg) {
        List<Map> rspInfo = (ArrayList<Map>) mixMap.get("rspInfo");
        List<Map> phoneRspInfo = (ArrayList<Map>) phoneMap.get("rspInfo");
        Object provOrderId = "";
        Object provOrderId2 = rspInfo.get(0).get("bssOrderId");
        submitMap.put("provOrderId", provOrderId2);
        submitMap.put("orderNo", msg.get("orderNo"));
        if ("bymc".equals(msg.get("tradeMethod"))) {
            submitMap.put("operationType", "02");// 02-订单取消
        }
        List<Map> subOrderSubReq = new ArrayList<Map>();
        Integer totalFee = 0;
        List<Map> provinceOrderInfo = (ArrayList<Map>) rspInfo.get(0).get("provinceOrderInfo");
        if (!IsEmptyUtils.isEmpty(provinceOrderInfo)) {
            totalFee = Integer.valueOf(provinceOrderInfo.get(0).get("totalFee").toString());
        }
        subOrderSubReq.add(dealFeelInfo(rspInfo));
        for (Map rspMap : phoneRspInfo) {
            List<Map> provinceOrder = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrder && provinceOrder.size() > 0) {
                for (Map orderInfo : provinceOrder) {
                    if (provOrderId2.equals(orderInfo.get("subProvinceOrderId"))) {
                        continue;
                    }
                    totalFee = totalFee + Integer.valueOf(orderInfo.get("totalFee").toString());
                    provOrderId = orderInfo.get("subOrderId");
                }
            }
        }
        List<Map> feeList = dealFee(phoneRspInfo);
        subOrderSubReq.add(dealFeelInfo(phoneRspInfo));
        msg.put("retFee", feeList);
        msg.put("provOrderId", provOrderId);
        msg.put("provOrderId2", provOrderId2);
        submitMap.put("subOrderSubReq", subOrderSubReq);
        submitMap.put("origTotalFee", totalFee);
        submitMap.put("cancleTotalFee", "0");
        return submitMap;
    }

    /**
     * 成员预提交
     */

    private Map prePhoneNumberInfo(Exchange exchange, Map msg, Map threePartInfo, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        List<Map> paraList = (List<Map>) threePartInfo.get("para");
        if (IsEmptyUtils.isEmpty(paraList)) {
            throw new EcAopServerBizException("9999", "北六ESS未返回PARA信息");
        }
        Object prodTariffCode = "";// 当前主产品资费编码
        Object prodTariffDesc = "";// 当前主产品资费名称
        Object bDiscntCode = "";// 资费编码
        String cycle = "12";
        String itemId = "";
        String itemId1 = (String) GetSeqUtil
                .getSeqFromCb(pmp[6], ExchangeUtils.ofCopy(exchange, msg), "seq_item_id", 1).get(0);
        msg.put("discntItemId", itemId1);
        for (Map para : paraList) {
            if ("PROD_TARIFF_CODE".equals(para.get("paraId"))) {
                prodTariffCode = para.get("paraValue");
            }
            if ("PROD_TARIFF_DESCRIBE".equals(para.get("paraId"))) {
                prodTariffDesc = para.get("paraValue");
            }
        }
        List<Map> userInfo = (List<Map>) threePartInfo.get("userInfo");
        if (IsEmptyUtils.isEmpty(userInfo)) {
            throw new EcAopServerBizException("9999", "用户信息未返回");
        }
        for (Map user : userInfo) {
            List<Map> discntInfo = (List<Map>) user.get("discntInfo");
            for (Map discnt : discntInfo) {
                if (!prodTariffCode.equals(discnt.get("discntCode"))) {
                    continue;
                }
                itemId = (String) discnt.get("itemId");
                bDiscntCode = discnt.get("discntCode");
                List<Map> attrInfo = (ArrayList<Map>) discnt.get("attrInfo");
                if (!IsEmptyUtils.isEmpty(attrInfo)) {
                    for (Map attr : attrInfo) {
                        if ("monthnum".equals(attr.get("attrCode"))) {
                            cycle = (String) attr.get("attrValue");
                            msg.put("monthnum", cycle);
                        }
                        else if ("biaozhuncode".equals(attr.get("attrCode"))) {
                            msg.put("biaozhuncode", attr.get("attrValue"));
                        }
                        else if ("expiredealmode".equals(attr.get("attrCode"))) {
                            msg.put("expiredealmode", attr.get("attrValue"));
                        }
                        else if ("monthdepositrate".equals(attr.get("attrCode"))) {
                            msg.put("monthdepositrate", attr.get("attrValue"));
                        }
                        else if ("monthnum".equals(attr.get("attrCode"))) {
                            msg.put("monthnum", attr.get("attrValue"));
                        }
                        else if ("yearmoney".equals(attr.get("attrCode"))) {
                            msg.put("yearmoney", attr.get("attrValue"));
                        }
                        else if ("monthnumsale".equals(attr.get("attrCode"))) {
                            msg.put("monthnumsale", attr.get("attrValue"));
                        }
                    }
                }

                String startDate = discnt.get("startDate").toString();
                SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
                String yyEndDate = sdf.format(RDate.addMonths(sdf.parse(startDate), Integer.valueOf(cycle.toString())));
                String yyStartDate = yyEndDate.substring(0, 8) + "000000";
                yyEndDate = DateUtils.addSeconds(yyStartDate, -1);
                msg.put("itemId", itemId);
                msg.put("bDiscntCode", bDiscntCode);
                msg.put("itemId1", itemId1);
                msg.put("discntName", prodTariffDesc);
                msg.put("yyStartDate", yyStartDate);
                msg.put("yyEndDate", yyEndDate);
            }
        }
        if (flag) {
            ELKAopLogger.logStr("appTx: " + apptx + "  进来了 ");
            preExt4PreSubmit(ext, threePartInfo, msg);
        }
        ext.put("tradeItem", preDataForTradeItem(msg, true, flag));
        ext.put("tradeSubItem", preDataForTradeSubItem(msg, flag));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), false, flag);
        msg.put("base", base);
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        ELKAopLogger.logStr("调用成员预提交的msg: appTx: " + apptx + ", msg= " + msg);
        lan.preData(pmp[0], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.spec.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        retMap.put("startDate", ext.get("startDate"));
        retMap.put("endDate", ext.get("endDate"));
        return retMap;
    }

    /**
     * 虚拟预提交
     */
    private Map preMixNumberInfo(Exchange exchange, Map msg, Boolean flag) throws Exception {
        Exchange copyExchange = ExchangeUtils.ofCopy(exchange, msg);
        Map ext = new HashMap();
        ext.put("tradeUser", preDataForTradeUser(msg));
        ext.put("tradeItem", preDataForTradeItem(msg, false, flag));
        msg.put("ext", ext);
        Map base = preBaseData(msg, exchange.getAppCode(), true, flag);
        msg.put("base", base);
        msg.put("operTypeCode", "0");
        msg.put("ordersId", msg.get("orderId"));
        ELKAopLogger.logStr("调用虚拟预提交的msg: appTx: " + apptx + ", msg= " + msg);
        lan.preData(pmp[0], copyExchange);
        CallEngine.wsCall(copyExchange, "ecaop.comm.conf.url.OrdForNorthSer." + msg.get("province"));
        lan.xml2Json("ecaop.trades.spec.sUniTrade.template", copyExchange);

        Map retMap = copyExchange.getOut().getBody(Map.class);
        List<Map> rspInfoList = (ArrayList<Map>) retMap.get("rspInfo");
        if (IsEmptyUtils.isEmpty(rspInfoList)) {
            throw new EcAopServerBizException(MagicNumber.ONS_ERROR_CODE, "核心系统未返回订单信息.");
        }
        return retMap;
    }

    private List<Map> dealFee(List<Map> rspInfo) {
        List<Map> retFeeList = new ArrayList<Map>();
        Map fee = new HashMap();
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                for (Map provinceOrder : provinceOrderInfo) {
                    List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (!IsEmptyUtils.isEmpty(feeInfo)) {
                        for (Map info : feeInfo) {
                            fee.put("operateType", info.get("operateType"));
                            fee.put("feeMode", info.get("feeMode"));
                            fee.put("feeTypeCode", info.get("feeTypeCode"));
                            fee.put("feeTypeName", info.get("feeTypeName"));
                            fee.put("maxDerateFee", info.get("maxDerateFee"));
                            fee.put("fee", info.get("fee"));
                        }
                        retFeeList.add(fee);
                    }
                }
            }
        }
        return retFeeList;
    }

    private Map dealFeelInfo(List<Map> rspInfo) {
        Map subOrderSubMap = new HashMap();
        String subProvinceOrderId = (String) rspInfo.get(0).get("bssOrderId");
        String subOrderId = (String) rspInfo.get(0).get("provOrderId");
        for (Map rspMap : rspInfo) {
            List<Map> provinceOrderInfo = (List) rspMap.get("provinceOrderInfo");
            if (null != provinceOrderInfo && provinceOrderInfo.size() > 0) {
                for (Map provinceOrder : provinceOrderInfo) {
                    List<Map> feeInfo = (List<Map>) provinceOrder.get("preFeeInfoRsp");
                    if (!IsEmptyUtils.isEmpty(feeInfo)) {
                        for (Map fee : feeInfo) {
                            fee.put("feeCategory", fee.get("feeMode"));
                            fee.put("feeId", fee.get("feeTypeCode"));
                            fee.put("feeDes", fee.get("feeTypeName"));
                            fee.put("origFee", fee.get("oldFee"));
                            fee.put("realFee", fee.get("oldFee"));
                            fee.put("isPay", "0");
                            fee.put("calculateTag", "N");
                            fee.put("payTag", "1");
                            fee.put("calculateId", GetSeqUtil.getSeqFromCb());
                            fee.put("calculateDate", DateUtils.getDate());
                        }
                        subProvinceOrderId = (String) provinceOrder.get("subProvinceOrderId");
                        subOrderId = (String) provinceOrder.get("subOrderId");
                        subOrderSubMap.put("feeInfo", feeInfo);
                    }
                }
            }
        }
        subOrderSubMap.put("subProvinceOrderId", subProvinceOrderId);
        subOrderSubMap.put("subOrderId", subOrderId);
        return subOrderSubMap;
    }

    private Map preExt4PreSubmit(Map ext, Map threePartInfo, Map msg) throws Exception {
        ELKAopLogger.logStr("处理产品前的msg: appTx: " + apptx + ", msg= " + msg);
        List<Map> productInfo = (List<Map>) msg.get("productInfo");
        if (IsEmptyUtils.isEmpty(productInfo)) {
            throw new EcAopServerBizException("8888", "发起方未下发产品信息");
        }
        List<Map> oldTradeOther = new ArrayList<Map>();
        List<Map> oldTradeProduct = new ArrayList<Map>();
        List<Map> oldTradeProductType = new ArrayList<Map>();
        List<Map> newProductList = new ArrayList<Map>();
        String oldProductId = "";
        boolean hasMainProd = false; // 标识是否含有订购主产品,不含有订购主产品时,使用订购的附加产品ID,作为宽带主资费的产品ID获取资费信息
        for (Map product : productInfo) {
            Object optType = product.get("optType");
            if ("1".equals(optType)) {
                oldProductId = (String) product.get("oldProductId");
                Map backProdct = new YearPayN6Utils().dealBackProduct(product, threePartInfo);
                oldTradeOther.add((Map) backProdct.get("tradeOther"));
                oldTradeProductType.add((Map) backProdct.get("tradeProductType"));
                oldTradeProduct.add((Map) backProdct.get("tradeProduct"));
                ext.put("tradeOther", MapUtils.asMap("item", oldTradeOther));
            }
            else {
                if ("0".equals(product.get("productMode"))) {// 含有订购主产品
                    hasMainProd = true;
                }
                msg.put("newProductId", product.get("oldProductId"));
                product.put("productId", product.get("oldProductId"));
                product.put("firstMonBillMode", "02");
                newProductList.add(product);
            }
        }
        Object oldProductStartDate = GetDateUtils.getDate();
        List<Map> threeInfo = (List<Map>) threePartInfo.get("userInfo");
        for (Map user : threeInfo) {
            oldProductStartDate = user.get("openDate");
            List<Map> threeProduct = (List<Map>) user.get("productInfo");
            if (IsEmptyUtils.isEmpty(threeProduct)) {
                continue;
            }
            for (Map prod : threeProduct) {
                if (oldProductId.equals(prod.get("productId"))) {
                    oldProductStartDate = prod.get("productActiveTime");
                    break;
                }
            }
        }
        msg.put("oldProductStartDate", oldProductStartDate);
        if (!IsEmptyUtils.isEmpty(newProductList)) {
            ext.putAll(TradeManagerUtils.preProductInfo(newProductList, "00" + msg.get("province"), msg));
        }
        // 宽带需要传入资费节点,重新塞入discnt节点,作为新增资费下发
        List<Map> discntList = new ArrayList<Map>();
        if (null != msg.get("discntList")) {
            discntList = (List<Map>) msg.get("discntList");
        }
        List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
        if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
            for (int j = 0; j < broadDiscntInfo.size(); j++) {
                Map disMap = new HashMap();
                Object broadDiscntId = broadDiscntInfo.get(j).get("broadDiscntId");
                msg.put("elementId", broadDiscntId);
                msg.put("PROVINCE_CODE", "00" + msg.get("province"));
                msg.put("EPARCHY_CODE", msg.get("eparchyCode"));
                msg.put("PRODUCT_ID", ext.get("mproductId"));
                if (!hasMainProd) {// 不含有主产品订购时,公共方法处理完之后没有mproductId字段
                    msg.put("PRODUCT_ID", msg.get("newProductId"));
                }
                disMap = TradeManagerUtils.preProductInfoByBroadDiscntId(msg);
                discntList.add(disMap);
            }

            if (null != discntList && !discntList.isEmpty()) {
                msg.put("discntList", discntList);
            }
            ext.put("tradeDiscnt", new YearPayN6Utils().preDiscntData(msg));
        }
        YearPayN6Utils ypN6U = new YearPayN6Utils();
        Map userInfo = ypN6U.getInfoFromThreePart(threePartInfo, "userInfo");
        List<Map> tradeProductTypeItem = (List<Map>) ((Map) ext.get("tradeProductType")).get("item");
        oldTradeProductType.addAll(tradeProductTypeItem);
        List<Map> tradeProductItem = (List<Map>) ((Map) ext.get("tradeProduct")).get("item");
        oldTradeProduct.addAll(tradeProductItem);
        List<Map> svcInfo = null;// (List<Map>) userInfo.get("svcInfo");
        List<Map> oldTradeSvcItem = new ArrayList<Map>();
        if (!IsEmptyUtils.isEmpty(svcInfo)) {
            for (Map svc : svcInfo) {
                oldTradeSvcItem.add(ypN6U.preTradeSvcBackItem(userInfo, svc));
            }
        }
        List<Map> tradeSvc = (List<Map>) ((Map) ext.get("tradeSvc")).get("item");
        for (Map svc : tradeSvc) {
            svc.put("startDate", GetDateUtils.getNextMonthFirstDayFormat());
        }
        oldTradeSvcItem.addAll(tradeSvc);
        for (Map prod : oldTradeProduct) {
            prod.put("userId", msg.get("userId"));
        }
        // 传入主产品的品牌
        ext.put("tradeUser", new YearPayN6Utils().preTradeUserItem(msg));
        ext.put("tradeProductType", MapUtils.asMap("item", oldTradeProductType));
        ext.put("tradeProduct", MapUtils.asMap("item", oldTradeProduct));
        ext.put("tradeSvc", MapUtils.asMap("item", oldTradeSvcItem));
        Map tradeDiscnt = (Map) ext.get("tradeDiscnt");
        List<Map> tradeDiscntItemList = (List<Map>) tradeDiscnt.get("item");
        ext.put("tradeDiscnt", MapUtils.asMap("item", tradeDiscntItemList));
        List<Map> itemInfo = (List<Map>) tradeDiscnt.get("item");
        ext.put("startDate", itemInfo.get(0).get("startDate"));
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        int brandNumber = Integer.valueOf((String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber"));
        String endDate = sdf.format(RDate.addMonths(sdf.parse(ext.get("startDate").toString()), brandNumber));
        ext.put("endDate", DateUtils.addSeconds(endDate, -1));
        if (oldProductId.equals(msg.get("newProductId"))) {
            ext.remove("tradeProductType");
            ext.remove("tradeProduct");
        }
        ELKAopLogger.logStr("处理产品后的ext: appTx: " + apptx + ", ext= " + ext);
        return ext;
    }

    private Map preDataForTradeSubItem(Map msg, Boolean flag) {
        List<Map> Item = new ArrayList<Map>();
        Map tradeSubItem = new HashMap();
        String startDate = GetDateUtils.getDate();
        String endDate = "20501231235959";
        String disItemId = (String) msg.get("itemId1");
        if (flag) {// 融合宽带变更产品场景
            String delayType = "";
            String expireDealMode = "b";
            String delayDiscntId = "";
            List<Map> broadDiscntInfo = (List<Map>) msg.get("broadDiscntInfo");
            if (null != broadDiscntInfo && !broadDiscntInfo.isEmpty()) {
                delayType = (String) ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayType");
                expireDealMode = "1".equals(delayType) ? "a" : "3".equals(delayType) ? "t" : "b";
                msg.put("expireDealMode", expireDealMode);
                msg.put("broadDiscntId", broadDiscntInfo.get(0).get("broadDiscntId"));
                delayDiscntId = (String) ((Map) broadDiscntInfo.get(0).get("broadDiscntAttr")).get("delayDiscntId");
                msg.put("delayDiscntId", delayDiscntId);
            }
            String brandNumber = (String) ((List<Map>) msg.get("productInfo")).get(0).get("brandNumber");
            preTradeSubItem4SD(Item, msg, disItemId, new SuniTradeUtils(), brandNumber, expireDealMode);
            Item.add(lan.createTradeSubItem("BOOKTIME", "", (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("LINK_PHONE", (String) msg.get("contactPerson"), (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("COUNTRYFLAG", "", (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("CONTACT_CONFIRM", "1", (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("SPEED",
                    (String) ("".equals(msg.get("speedLevel")) ? "110008" : msg.get("speedLevel")),
                    (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("LINK_NAME", (String) msg.get("customerName"), (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("BILLINGRATE", msg.get("speedLevel"), (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("REMARK", "", (String) msg.get("itemId")));
            Item.add(lan.createTradeSubItem("TIME_SHARING", "0", (String) msg.get("itemId")));

        }
        else {
            if ("Y".equals(msg.get("expiredealmode"))) {
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "expireDealMode", "Y", startDate,
                        endDate));
            }
            else {
                Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "expireDealMode", "N", startDate,
                        endDate));
            }
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "monthdepositrate",
                    msg.get("monthdepositrate"), startDate,
                    endDate));
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "monthnum", msg.get("monthnum"),
                    startDate, endDate));
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "monthnumsale", msg.get("monthnumsale"),
                    startDate, endDate));
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "yearmoney", msg.get("yearmoney"),
                    startDate, endDate));
            // 应北六E要求,该字段由dealmode修改为wodealmode by wangmc 20180802
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "wodealmode", "RECHARGE", startDate, endDate));
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "biaozhuncode",
                    (String) msg.get("biaozhuncode"), startDate, endDate));
            Item.add(lan.createTradeSubItemC((String) msg.get("itemId"), "callBack", "0", startDate, endDate));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "TIME_SHARING", "0"));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "REMARK", "S30"));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "BILLINGRATE", "110008"));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "LINK_NAME", (String) msg.get("customerName")));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "CONTACT_CONFIRM", "1"));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "SPEED",
                    (String) ("".equals(msg.get("speedLevel")) ? "110008" : msg.get("speedLevel"))));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "LINK_PHONE",
                    (String) msg.get("contactPerson")));
            Item.add(lan.createTradeSubItemB2((String) msg.get("userId"), "BOOKTIME", ""));
        }
        tradeSubItem.put("item", Item);
        return tradeSubItem;
    }

    private void preTradeSubItem4SD(List<Map> items, Map inputMap, String userId, SuniTradeUtils lan,
            String brandNumber, String expireDealMode) {
        String startDate = DateUtils.getNextMonthFirstDay();
        String endDate = "20501231235959";
        items.add(lan.createSubItem4UserAttr(userId, "monthnum", brandNumber, startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "monthdepositrate", "1.0", startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "monthcyclefee", "100", startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "monthnumsale", inputMap.get("monthnumsale"), startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "yearmoney", "6000", startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "expiredealmode", expireDealMode, startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "biaozhuncode", inputMap.get("delayDiscntId"), startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "xieyicode", inputMap.get("broadDiscntId"), startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "xieyifee", "", startDate, endDate));
        // 应北六E要求,该字段由dealmode修改为wodealmode 变更附加产品的场景值要传ADD by wangmc 20180802
        items.add(lan.createSubItem4UserAttr(userId, "wodealmode", "ADD", startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "bcafixedhireflag", "0", startDate, endDate));
        items.add(lan.createSubItem4UserAttr(userId, "agrnotifynumber", "", startDate, endDate));
    }

    private Map preDataForTradeItem(Map msg, Boolean isCY, Boolean flag) throws Exception {
        List<Map> Item = new ArrayList<Map>();
        Map tradeItem = new HashMap();
        Item.add(LanUtils.createTradeItem("DEVELOP_STAFF_ID", msg.get("operatorId")));
        Item.add(LanUtils.createTradeItem("DEVELOP_DEPART_ID", msg.get("channelId")));
        Item.add(LanUtils.createTradeItem("STANDARD_KIND_CODE", msg.get("channelType")));
        Item.add(LanUtils.createTradeItem("RES_PRE_ORDER", "0"));
        Item.add(LanUtils.createTradeItem("MARKETING_MODE", "1"));
        Item.add(LanUtils.createTradeItem("ECS_ORDER_ID", msg.get("orderNo")));
        if (isCY) {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "1"));
        }
        else {
            Item.add(LanUtils.createTradeItem("ALONE_TCS_COMP_INDEX", "2"));
        }
        if (isCY) {
            Item.add(LanUtils.createTradeItem("LINK_PHONE", msg.get("contactPerson")));
            Item.add(LanUtils.createTradeItem("IS_SAME_CUST", msg.get("custId")));
            Item.add(LanUtils.createTradeItem("LINK_NAME", msg.get("customerName")));
            Item.add(LanUtils.createTradeItem("IMMEDIACY_INFO", "0"));
            Item.add(LanUtils.createTradeItem("OPER_CODE", "3"));
            Item.add(LanUtils.createTradeItem("COMP_DEAL_STATE", "4"));
            Item.add(LanUtils.createTradeItem("ROLE_CODE_B", "g"));
        }
        if (flag && isCY) {
            Item.add(LanUtils.createTradeItem("ECS_SINGLE_FLAG", "1"));
        }
        tradeItem.put("item", Item);
        return tradeItem;
    }

    // 拼装TRADE_USER
    private Map preDataForTradeUser(Map msg) {
        try {
            Map tradeUser = new HashMap();
            List<Map> itemList = new ArrayList<Map>();
            Map item = new HashMap();
            item.put("xDatatype", "NULL");
            // item.put("userId", msg.get("mixUserId"));
            // item.put("custId", msg.get("custId"));
            // item.put("usecustId", msg.get("custId"));
            item.put("eparchyCode", msg.get("eparchyCode"));
            item.put("cityCode", msg.get("district"));
            item.put("productTypeCode", "COMP");
            item.put("userPasswd", "123456");
            item.put("userTypeCode", "0");
            item.put("scoreValue", "0");
            item.put("creditClass", "0");
            item.put("basicCreditValue", "0");
            item.put("creditValue", "0");
            item.put("acctTag", "0");
            item.put("prepayTag", "0");
            item.put("inDate", GetDateUtils.getDate());
            item.put("openDate", GetDateUtils.getDate());
            item.put("openMode", "0");
            item.put("openDepartId", msg.get("channelId"));
            item.put("openStaffId", msg.get("operatorId"));
            item.put("inDepartId", msg.get("channelId"));
            item.put("inStaffId", msg.get("operatorId"));
            item.put("removeTag", "0");
            item.put("userStateCodeset", "0");
            item.put("mputeMonthFee", "0");
            item.put("developDate", GetDateUtils.getDate());
            item.put("developStaffId", msg.get("recomPersonId"));
            item.put("developDepartId", msg.get("channelId"));
            item.put("inNetMode", "0");
            itemList.add(item);
            tradeUser.put("item", itemList);
            return tradeUser;
        }
        catch (Exception e) {
            e.printStackTrace();
            throw new EcAopServerBizException("9999", "拼装TRADE_USER节点报错" + e.getMessage());
        }
    }

    private Map preBaseData(Map msg, String appCode, Boolean isMixNum, Boolean flag) {
        String date = GetDateUtils.getDate();
        Map base = new HashMap();
        base.put("tradeLcuName", "TCS_CompTradeMoveReg");
        base.put("startDate", date);
        base.put("olcomTag", "0");
        base.put("areaCode", msg.get("eparchyCode"));
        base.put("foregift", "0");
        base.put("execTime", date);
        base.put("acceptDate", date);
        base.put("chkTag", "0");
        base.put("operFee", "0");
        base.put("cancelTag", "0");
        base.put("endAcycId", "203701");
        base.put("startAcycId", RDate.currentTimeStr("yyyyMM"));
        base.put("acceptMonth", RDate.currentTimeStr("MM"));
        base.put("advancePay", "0");
        base.put("inModeCode", new ChangeCodeUtils().getInModeCode(appCode));
        base.put("tradeStaffId", msg.get("operatorId"));
        base.put("checkTypeCode", "8");
        base.put("termIp", "10.253.0.2");
        base.put("eparchyCode", msg.get("eparchyCode"));
        base.put("cityCode", msg.get("district"));
        base.put("tradeDepartId", msg.get("departId"));
        base.put("productId", msg.get("productId"));
        base.put("custId", msg.get("custId"));
        base.put("usecustId", msg.get("custId"));
        base.put("custName", msg.get("custName"));
        base.put("acctId", msg.get("acctId"));
        base.put("feeState", "0");
        base.put("actorName", "");
        base.put("actorCertTypeId", "");
        base.put("actorPhone", "");
        base.put("actorCertNum", "");
        base.put("contact", "");
        base.put("contactPhone", "");
        base.put("contactAddress", "");
        base.put("remark", "总部ECS订单");
        base.put("nextDealTag", "Z");
        base.put("subscribeId", msg.get("orderId"));
        if (isMixNum) {
            base.put("productId", msg.get("mixProduct"));
            base.put("userId", msg.get("mixUserId"));
            base.put("tradeId", msg.get("orderId"));
            base.put("userDiffCode", "8421");
            base.put("brandCode", "COMP");
            base.put("tradeTypeCode", "0110");
            base.put("netTypeCode", "00CP");
            base.put("serialNumber", msg.get("mixNumber"));
        }
        else {
            base.put("productId", msg.get("productId"));
            base.put("userId", msg.get("userId"));
            base.put("tradeId", msg.get("tradeId"));
            base.put("userDiffCode", "00");
            base.put("brandCode", "FTTH");
            base.put("tradeTypeCode", "0340");
            base.put("netTypeCode", "0040");
            base.put("serialNumber", msg.get("serialNumber"));
            if (flag) {
                base.put("productId", msg.get("newProductId"));
            }
        }
        return base;
    }
}