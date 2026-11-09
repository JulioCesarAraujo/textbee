package com.vernu.sms;

import android.content.Context;

import com.vernu.sms.managers.NetworkManager;
import com.vernu.sms.services.GatewayApiService;

import java.io.IOException;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiManager {
    private static GatewayApiService apiService;
    private static String apiServiceBaseUrl;
    private static Context appContext;

    /** Called once by the Application, so the base URL can follow the offline mode settings. */
    public static void init(Context context) {
        appContext = context.getApplicationContext();
    }

    /**
     * API base URL in use now: production, or the offline server when offline
     * mode is on (see NetworkManager).
     */
    public static String currentBaseUrl() {
        if (appContext == null) return AppConstants.API_BASE_URL;
        return new NetworkManager(appContext).getServerUrl();
    }

    public static synchronized GatewayApiService getApiService() {
        String baseUrl = currentBaseUrl();
        if (apiService == null || !baseUrl.equals(apiServiceBaseUrl)) {
            apiService = createApiService(baseUrl);
            apiServiceBaseUrl = baseUrl;
        }
        return apiService;
    }

    /** Adds the client name to every request. See AppConstants.CLIENT_NAME. */
    static OkHttpClient createHttpClient() {
        return new OkHttpClient.Builder()
                .addInterceptor(new Interceptor() {
                    @Override
                    public Response intercept(Chain chain) throws IOException {
                        Request request = chain.request()
                                .newBuilder()
                                .header(AppConstants.CLIENT_HEADER, AppConstants.CLIENT_NAME)
                                .build();
                        return chain.proceed(request);
                    }
                })
                .build();
    }

    private static GatewayApiService createApiService(String baseUrl) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(createHttpClient())
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        return retrofit.create(GatewayApiService.class);
    }
}
