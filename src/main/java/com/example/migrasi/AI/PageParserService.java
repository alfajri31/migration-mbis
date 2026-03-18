package com.example.migrasi.AI;

import com.example.migrasi.util.OCRUtil;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class PageParserService {

    public List<Map<String,String>> toJson(Map<String,String> map,String path) {

        path = "images/askrindo/pages/login";

        Map<String,String> parser = OCRUtil.extractFromResources(path);

        return new ArrayList<>();
    }
}
