package edu.northwestern.ssa.api;

import org.json.JSONArray;
import org.json.JSONObject;

import software.amazon.awssdk.http.HttpExecuteResponse;
import software.amazon.awssdk.http.SdkHttpMethod;
import software.amazon.awssdk.http.AbortableInputStream;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Response;
import java.io.*;
import java.util.HashMap;
import java.util.Optional;

import edu.northwestern.ssa.*;

@Path("/search")
public class Search {

    private String host = Config.getParam("ELASTIC_SEARCH_HOST");
    private String index = Config.getParam("ELASTIC_SEARCH_INDEX");
    /** when testing, this is reachable at http://localhost:8080/api/search?query=hello */
    @GET
    public Response getMsg(@QueryParam("query") String q,
                           @QueryParam("language") String language,
                           @QueryParam("date") String date,
                           @QueryParam("count") String count,
                           @QueryParam("offset") String offset) throws IOException {
        if (q==null) return Response.status(400).build();
        AwsSignedRestRequest ES = new AwsSignedRestRequest("es");
        HashMap<String,String> query = new HashMap<>();
        q = q.replace(" "," AND ");
        q = "txt:("+q+")";
        if (language!=null) q+= (" AND lang:"+language);
        if (date != null) q+= (" AND date:"+date);
        query.put("q",q);
//        System.out.print(q);
        query.put("track_total_hits","true");
        query.put("preference","3839c806-84a4-11ea-bc55-0242ac130003");
        if (count!=null) query.put("size",count);
        if (offset!=null) query.put("from",offset);
        HttpExecuteResponse res = ES.restRequest(SdkHttpMethod.GET,host,index+"/_search",Optional.of(query));
        if (!res.httpResponse().isSuccessful()) return Response.status(400).build();
        AbortableInputStream input = res.responseBody().get();
        byte[] buffer = new byte[1024];
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        String jsonString = output.toString("UTF-8");
        output.close();
        input.close();
        JSONObject document = new JSONObject(jsonString);
        JSONObject results = new JSONObject();
        results.put("total_results",document.getJSONObject("hits").getJSONObject("total").getInt("value"));
        JSONArray hits = document.getJSONObject("hits").getJSONArray("hits");
        results.put("returned_results",hits.length());
        JSONArray articles = new JSONArray();
        for (int i=0; i<hits.length();i++){
            JSONObject item = new JSONObject();
            item.put("title",hits.getJSONObject(i).getJSONObject("_source").getString("title"));
            item.put("url",hits.getJSONObject(i).getJSONObject("_source").getString("url"));
            item.put("txt",hits.getJSONObject(i).getJSONObject("_source").getString("txt"));
            if (hits.getJSONObject(i).getJSONObject("_source").has("date"))
                item.put("date",hits.getJSONObject(i).getJSONObject("_source").getString("date"));
            if (hits.getJSONObject(i).getJSONObject("_source").has("lang"))
                item.put("lang",hits.getJSONObject(i).getJSONObject("_source").getString("lang"));
            articles.put(item);
        }
        results.put("articles",articles);
        return Response.status(200).type("application/json").entity(results.toString(4))
                // below header is for CORS
                .header("Access-Control-Allow-Origin", "*").build();
    }
}
