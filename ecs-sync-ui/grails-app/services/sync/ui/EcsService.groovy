package sync.ui

import com.emc.object.Protocol
import com.emc.object.s3.S3Client
import com.emc.object.s3.S3Config
import com.emc.object.s3.S3Exception
import com.emc.object.s3.jersey.S3JerseyClient
import com.emc.object.s3.request.ListObjectsRequest
import grails.transaction.Transactional
import org.springframework.beans.BeanUtils

@Transactional(readOnly = true)
class EcsService {
    static clientMap = [:]

    List<String> listConfigObjects(String prefix) {
        def uiConfig = getUiConfig()
        def request = new ListObjectsRequest(uiConfig.configBucket).withPrefix(prefix).withDelimiter('/')
        getEcsClient(uiConfig).listObjects(request).objects.collect { it.key }
    }

    boolean configObjectExists(String key) {
        if (!key) return false;
        try {
            def uiConfig = getUiConfig()
            getEcsClient(uiConfig).getObjectMetadata(uiConfig.configBucket, key)
            return true;
        } catch (S3Exception e) {
            if (e.httpCode == 404) return false;
            else throw e
        }
    }

    public <T> T readConfigObject(String key, Class<T> resultType) {
        def uiConfig = getUiConfig()
        getEcsClient(uiConfig).readObject(uiConfig.configBucket, key, resultType)
    }

    void writeConfigObject(String key, content, String contentType) {
        def uiConfig = getUiConfig()
        getEcsClient(uiConfig).putObject(uiConfig.configBucket, key, content, contentType)
    }

    void deleteConfigObject(String key) {
        def uiConfig = getUiConfig()
        if (key) getEcsClient(uiConfig).deleteObject(uiConfig.configBucket, key)
    }

    URL configObjectQuickLink(String key) {
        def uiConfig = getUiConfig()
        getEcsClient(uiConfig).getPresignedUrl(uiConfig.configBucket, key, 4.hours.from.now)
    }

    void writeConfig(UiConfig uiConfig) {
        def ecs = getEcsClient(uiConfig)
        if (!ecs.bucketExists(uiConfig.configBucket)) ecs.createBucket(uiConfig.configBucket)
        ecs.putObject(uiConfig.configBucket, 'ui-config.xml', uiConfig, 'application/xml')
    }

    void readUiConfig(UiConfig uiConfig) {
        def ecs = getEcsClient(uiConfig);
        BeanUtils.copyProperties(ecs.readObject(uiConfig.configBucket, 'ui-config.xml', UiConfig.class), uiConfig, 'id')
    }

    UiConfig readUiConfig() {
        def uiConfig = getUiConfig()
        readUiConfig(uiConfig)
        return uiConfig
    }

    UiConfig getUiConfig() {
        def uiConfig = UiConfig.first([readOnly: true])
        if (uiConfig == null) throw new S3Exception("Missing ECS config", 0)
        return uiConfig
    }

    private static S3Client getEcsClient(UiConfig uiConfig) {
        def key = "${uiConfig.accessKey}:${uiConfig.secretKey}@${uiConfig.hosts}"
        def client = clientMap[key]
        if (!client) {
            client = new S3JerseyClient(new S3Config(Protocol.valueOf(uiConfig.protocol.toUpperCase()), uiConfig.hosts.split(','))
                    .withPort(uiConfig.port).withIdentity(uiConfig.accessKey).withSecretKey(uiConfig.secretKey))
            clientMap[key] = client
        }
        return client as S3Client
    }
}
