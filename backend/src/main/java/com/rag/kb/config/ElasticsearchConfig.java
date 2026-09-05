package com.rag.kb.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@Configuration
public class ElasticsearchConfig {

    private final RagProperties props;

    public ElasticsearchConfig(RagProperties props) {
        this.props = props;
    }

    @Bean(destroyMethod = "close")
    public ElasticsearchClient elasticsearchClient() {
        RagProperties.Es es = props.getEs();
        List<HttpHost> hosts = new ArrayList<>();
        for (String uri : es.getUris().split(",")) {
            hosts.add(HttpHost.create(uri.trim()));
        }
        RestClientBuilder builder = RestClient.builder(hosts.toArray(new HttpHost[0]));
        if (es.getUsername() != null && !es.getUsername().isBlank()) {
            CredentialsProvider creds = new BasicCredentialsProvider();
            creds.setCredentials(AuthScope.ANY,
                    new UsernamePasswordCredentials(es.getUsername(), es.getPassword()));
            builder.setHttpClientConfigCallback(h -> h.setDefaultCredentialsProvider(creds));
        }
        builder.setRequestConfigCallback(c -> c.setConnectTimeout(5000).setSocketTimeout(60000));
        RestClient restClient = builder.build();
        ElasticsearchTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());
        return new ElasticsearchClient(transport);
    }

    /** 判断 ES 集群是否就绪（启动时尝试连接，失败不阻塞应用，后续可用） */
    public static boolean isReachable(ElasticsearchClient client) {
        try {
            return client.ping().value();
        } catch (Exception e) {
            return false;
        }
    }
}
