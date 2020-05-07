package edu.northwestern.ssa;

import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;
import org.json.JSONObject;
import org.jsoup.Jsoup;
import org.jsoup.UncheckedIOException;
import org.jsoup.nodes.Document;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.*;

public class App {
    public static void main(String[] args) throws IOException {
        S3Client s3 = S3Client.builder()
                .region(Region.US_EAST_1)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofMinutes(30)).build())
                .build();
        String targetFile;
        String host =  System.getenv("ELASTIC_SEARCH_HOST");
        String index = System.getenv("ELASTIC_SEARCH_INDEX");
        if (!System.getenv().containsKey("COMMON_CRAWL_FILENAME") || System.getenv("COMMON_CRAWL_FILENAME").isEmpty()){
            ListObjectsV2Request req = ListObjectsV2Request.builder().bucket("commoncrawl").prefix("crawl-data/CC-NEWS/").build();
            ListObjectsV2Response res;
            ArrayList<String> l = new ArrayList<>();
            do{
                res = s3.listObjectsV2(req);
                req = ListObjectsV2Request.builder().bucket("commoncrawl").prefix("crawl-data/CC-NEWS/").continuationToken(res.nextContinuationToken()).build();
                for (S3Object ob: res.contents()){
                    l.add(ob.key());
                }
            }while (res.isTruncated());
            l.sort(String::compareTo);
            targetFile = l.get(l.size()-1);
        }else{
            targetFile = System.getenv("COMMON_CRAWL_FILENAME");
        }
        InputStream is = new FileInputStream("latest_file");
        BufferedReader BR = new BufferedReader(new InputStreamReader(is));
        String line = BR.readLine();
        BR.close();
        if (targetFile.equals(line)) {
            s3.close();
            return;
        }
        FileWriter FW = new FileWriter("latest_file");
        FW.write(targetFile);
        FW.close();
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket("commoncrawl")
                .key(targetFile)
                .build();
//        File f = new File("data.warc.gz");
//        s3.getObject(request,ResponseTransformer.toFile(f));
//        ArchiveReader reader = WARCReaderFactory.get(f);
        ResponseInputStream<GetObjectResponse> s3Object = s3.getObject(request, ResponseTransformer.toInputStream());
        ArchiveReader reader = WARCReaderFactory.get(targetFile,s3Object,true);
//        ElasticSearch ES = new ElasticSearch("es");
//        if(!ES.createIndex(host,index)){
//            throw new IOException("index creation fault");
//        }
//        ES.close();
        int count = 0;
        ExecutorService threadpool = new ThreadPoolExecutor(50,
                1000,
                0L,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(30000),
                Executors.defaultThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy()
                );
        for (ArchiveRecord record : reader) {
            byte[] buffer = new byte[400];
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            int length;
            while ((length = record.read(buffer)) != -1) {
                output.write(buffer, 0, length);
            }
            String htmlString = output.toString("UTF-8");
            output.close();
            int idx = htmlString.indexOf("\r\n\r\n");
            if (idx == -1) continue;
            String body = htmlString.substring(idx+4);
            Document doc;
            try {
                doc = Jsoup.parse(body);
            } catch (UncheckedIOException e) {
                continue;
            }
            JSONObject document = new JSONObject();
            if ( doc.text().isEmpty() || record.getHeader().getUrl().isEmpty()
                    || !doc.select("html").first().attr("lang").equals("en")) continue;
            document.put("title", doc.title());
            doc.select("title").remove();
            document.put("txt", doc.text());
            document.put("url", record.getHeader().getUrl());
            document.put("lang",doc.select("html").first().attr("lang"));
            Date currentTime = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
            document.put("date",formatter.format(currentTime));
            threadpool.execute(() -> {
                ElasticSearch ES1 = new ElasticSearch("es");
                while (true){
                    try{
                        if(ES1.postDocument(host, index, document)) break;
                    }catch (IOException ignored) {}}
                try{
                    ES1.close();
                }catch (IOException ignored) {}
            });
            count++;
        }
        s3Object.close();
        threadpool.shutdown();
        s3.close();
        System.out.println(count);
    }
}
