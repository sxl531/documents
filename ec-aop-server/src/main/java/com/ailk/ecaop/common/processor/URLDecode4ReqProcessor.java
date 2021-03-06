package com.ailk.ecaop.common.processor;

import java.net.URLDecoder;

import org.n3r.core.tag.EcRocTag;
import org.n3r.ecaop.core.exception.request.EcAopRequestBizException;

@EcRocTag("urlDecode4Req")
public class URLDecode4ReqProcessor extends URLCode4ReqProcessor {

    @Override
    public String doCode(String con) {

        try {
            return URLDecoder.decode(con, "UTF-8");
        }
        catch (Exception e) {
            throw new EcAopRequestBizException("Can't parse Encode str", e);
        }

    }

}
