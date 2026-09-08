package com.rescueroom.backend;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/", "file:../rescue-room/assets/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
        registry.addResourceHandler("/*.css", "/*.js", "/*.png", "/index.html")
                .addResourceLocations("classpath:/static/", "file:../rescue-room/")
                .setCacheControl(CacheControl.noStore().mustRevalidate());
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }
}
