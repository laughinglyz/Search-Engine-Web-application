package edu.northwestern.ssa;

import org.json.JSONObject;
import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class ElasticSearch extends AwsSignedRestRequest{

    /**
     * @param serviceName would be "es" for Elasticsearch
     */
    ElasticSearch(String serviceName) {
        super(serviceName);
    }

    public boolean createIndex(String host, String index) throws IOException {
        HttpExecuteResponse res = restRequest(SdkHttpMethod.PUT,host,index, Optional.empty());
        if (res.responseBody().isPresent()) res.responseBody().get().close();
        return res.httpResponse().isSuccessful();
    }

    public boolean postDocument(String host, String index, JSONObject document) throws IOException {
        HttpExecuteResponse res = restRequest(SdkHttpMethod.POST,host,index+"/_doc/",Optional.empty(),Optional.of(document));
        if (res.responseBody().isPresent()) res.responseBody().get().close();
        return res.httpResponse().isSuccessful();
    }

    public boolean search(String host, String index, Map<String, String> query) throws IOException{
        HttpExecuteResponse res = restRequest(SdkHttpMethod.GET,host,index+"/_search",Optional.of(query));
        if (res.responseBody().isPresent()) res.responseBody().get().close();
        return res.httpResponse().isSuccessful();
    }
}
