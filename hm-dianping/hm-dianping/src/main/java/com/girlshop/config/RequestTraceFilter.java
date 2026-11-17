package com.girlshop.config;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import javax.servlet.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.*;
@Component public class RequestTraceFilter extends OncePerRequestFilter {
    private static final Logger LOG=LoggerFactory.getLogger(RequestTraceFilter.class);
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
        String supplied=request.getHeader("X-Request-Id");
        String id=supplied!=null&&supplied.matches("[A-Za-z0-9-]{1,64}")?supplied:UUID.randomUUID().toString();
        MDC.put("requestId",id);response.setHeader("X-Request-Id",id);long start=System.nanoTime();
        try{chain.doFilter(request,response);}finally{long ms=(System.nanoTime()-start)/1000000;
            if(ms>=1000)LOG.warn("慢请求 method={} path={} durationMs={} status={}",request.getMethod(),request.getRequestURI(),ms,response.getStatus());MDC.remove("requestId");}
    }
}
