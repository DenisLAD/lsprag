package ru.sbrf.uddk.ai.testing.lsprag.llm;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.credentialStore.Credentials;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.diagnostic.Logger;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import ru.sbrf.uddk.ai.testing.lsprag.LspragSettingsState;

import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

public class ClientUtils {

    private static final Logger LOGGER = Logger.getInstance(ClientUtils.class.getName());

    /**
     * Создает HttpClient с поддержкой SSL
     */
    public static HttpClient createHttpClient(LspragSettingsState settings) {
        try {
            HttpClientBuilder builder = HttpClients.custom();

            // Создаем SSLContext
            SSLContext sslContext = createSSLContext(settings);

            if (sslContext != null) {
                builder.setSSLContext(sslContext);
                LOGGER.info("SSL configured successfully");
            } else {
                LOGGER.warn("SSLContext creation failed, using default configuration");
                return HttpClients.createDefault();
            }

            return builder.build();
        } catch (Exception e) {
            LOGGER.error("Failed to create HttpClient with SSL configuration", e);
            return HttpClients.createDefault();
        }
    }

    /**
     * Создает SSLContext с поддержкой KeyStore и TrustStore
     */
    private static SSLContext createSSLContext(LspragSettingsState settings) {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");

            // Загружаем KeyManager (клиентские сертификаты)
            KeyManager[] keyManagers = loadKeyManagers(settings);

            // Загружаем TrustManager (доверенные сертификаты)
            TrustManager[] trustManagers = loadTrustManagers(settings);

            // Инициализируем SSLContext
            sslContext.init(keyManagers, trustManagers, new SecureRandom());

            return sslContext;

        } catch (Exception e) {
            LOGGER.error("Failed to create SSLContext", e);
            return null;
        }
    }

    /**
     * Загружает KeyManagers из KeyStore
     */
    private static KeyManager[] loadKeyManagers(LspragSettingsState settings) {
        if (!settings.isUseKeystore() || settings.getKeystorePath() == null
                || settings.getKeystorePath().isEmpty()) {
            return null;
        }

        try {
            String keystorePassword = getPasswordFromSafe("lsprag.keystore.password");
            if (keystorePassword == null) {
                LOGGER.warn("Keystore password not found");
                return null;
            }

            KeyStore keyStore = loadKeyStore(settings.getKeystorePath(), keystorePassword);
            if (keyStore == null) {
                LOGGER.warn("Failed to load KeyStore");
                return null;
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, keystorePassword.toCharArray());

            LOGGER.info("KeyManagers loaded successfully");
            return kmf.getKeyManagers();

        } catch (Exception e) {
            LOGGER.error("Failed to load KeyManagers", e);
            return null;
        }
    }

    /**
     * Загружает TrustManagers из TrustStore или использует системные
     */
    private static TrustManager[] loadTrustManagers(LspragSettingsState settings) {
        try {
            // Если указан свой TrustStore
            if (settings.getKeystorePath() != null && !settings.getKeystorePath().isEmpty()) {
                String truststorePassword = getPasswordFromSafe("lsprag.keystore.password");
                if (truststorePassword == null) {
                    LOGGER.warn("Truststore password not found");
                    return getSystemTrustManagers();
                }

                KeyStore trustStore = loadKeyStore(settings.getKeystorePath(), truststorePassword);
                if (trustStore != null) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    LOGGER.info("Using custom TrustStore: " + settings.getKeystorePath());
                    return tmf.getTrustManagers();
                }
            }

            // Если указан отдельный файл сертификата
            if (settings.getKeystorePath() != null && !settings.getKeystorePath().isEmpty()) {
                KeyStore trustStore = createTrustStoreWithCertificate(settings.getKeystorePath());
                if (trustStore != null) {
                    TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                            TrustManagerFactory.getDefaultAlgorithm());
                    tmf.init(trustStore);
                    LOGGER.info("Using certificate: " + settings.getKeystorePath());
                    return tmf.getTrustManagers();
                }
            }

            // Используем системный TrustStore
            return getSystemTrustManagers();

        } catch (Exception e) {
            LOGGER.error("Failed to load TrustManagers", e);
            return getSystemTrustManagers();
        }
    }

    /**
     * Получает системные TrustManagers
     */
    private static TrustManager[] getSystemTrustManagers() {
        try {
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init((KeyStore) null); // использует системный truststore
            LOGGER.info("Using system default TrustStore");
            return tmf.getTrustManagers();
        } catch (Exception e) {
            LOGGER.error("Failed to load system TrustManagers", e);
            return null;
        }
    }

    /**
     * Создает TrustStore из одного сертификата
     */
    private static KeyStore createTrustStoreWithCertificate(String certificatePath) {
        try {
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);

            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                CertificateFactory cf = CertificateFactory.getInstance("X.509");
                Certificate cert = cf.generateCertificate(fis);
                trustStore.setCertificateEntry("server-cert", cert);
                LOGGER.info("Certificate loaded: " + certificatePath);
            }

            return trustStore;
        } catch (Exception e) {
            LOGGER.error("Failed to load certificate: " + certificatePath, e);
            return null;
        }
    }

    /**
     * Загружает KeyStore из файла
     */
    private static KeyStore loadKeyStore(String path, String password) {
        if (path == null || path.isEmpty()) {
            return null;
        }

        File file = new File(path);
        if (!file.exists()) {
            LOGGER.warn("File not found: " + path);
            return null;
        }

        String keystoreType = getKeyStoreType(path);

        try (FileInputStream fis = new FileInputStream(file)) {
            KeyStore keyStore = KeyStore.getInstance(keystoreType);
            keyStore.load(fis, password != null ? password.toCharArray() : null);
            LOGGER.info("KeyStore loaded: " + path + " (type: " + keystoreType + ")");
            return keyStore;
        } catch (Exception e) {
            LOGGER.error("Failed to load KeyStore: " + path, e);
            return null;
        }
    }

    /**
     * Определяет тип KeyStore по расширению
     */
    private static String getKeyStoreType(String path) {
        if (path.endsWith(".p12") || path.endsWith(".pfx")) {
            return "PKCS12";
        } else if (path.endsWith(".jks")) {
            return "JKS";
        }
        return KeyStore.getDefaultType();
    }

    /**
     * Получает пароль из PasswordSafe
     */
    private static String getPasswordFromSafe(String key) {
        try {
            CredentialAttributes attrs = new CredentialAttributes(key);
            Credentials credentials = PasswordSafe.getInstance().get(attrs);
            if (credentials != null) {
                return credentials.getPasswordAsString();
            }
        } catch (Exception e) {
            LOGGER.warn("Failed to get password for " + key, e);
        }
        return null;
    }
}