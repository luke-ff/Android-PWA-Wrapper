package at.webpos.softpay;

import static android.content.ContentValues.TAG;

import static io.softpay.client.OutputTypes.JSON;
import static io.softpay.client.samples.SamplesUtil.POS_APP_ABORT_CODE;
import static io.softpay.client.samples.SamplesUtil.POS_APP_ABORT_REASON;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.WebView;

import java.util.Objects;
import java.util.concurrent.Callable;

import io.softpay.client.Client;
import io.softpay.client.ClientOptions;
import io.softpay.client.Failure;
import io.softpay.client.Manager;
import io.softpay.client.Request;
import io.softpay.client.Softpay;
import io.softpay.client.SoftpayTarget;
import io.softpay.client.Tier;
import io.softpay.client.domain.Amount;
import io.softpay.client.domain.Integrator;
import io.softpay.client.domain.Transaction;
import io.softpay.client.transaction.PaymentTransaction;
import io.softpay.client.transaction.TransactionManager;
import io.softpay.client.transaction.TransactionRequestOptions;

public class SoftPayImpl {

    final Context applicationContext;
    final WebView webView;
    Client client = null;
    private int pw = 42;
    private Request currentRequest = null;

    public SoftPayImpl(final Context applicationContext, WebView webView) {
        this.applicationContext = applicationContext;
        this.webView = webView;
/*
        setupClient( "SPAY-spingmbh",
                "AcmeSpinGmbH",
                "f53eef50ec5049088abff4e2edfd0757",
                SoftpayTarget.SANDBOX
        );
*/
        setupClient( "Prod-spingmbh",
                "SpinGmbH",
                "0bbeda7784d441688f5b90e31a6df17b",
                SoftpayTarget.ANY
        );


    }

    private Integrator createIntegrator(final String id, final String merchant, final String secret, SoftpayTarget softpayTarget) {
        return new Integrator.Builder()
                .id(id, merchant)
                .secret( secret.toCharArray() )
                .target(softpayTarget)
                .label("hobex")
                .build();
    }


    private void setupClient(final String id, final String merchant, final String secret, SoftpayTarget softpayTarget) {
        final Integrator integrator = createIntegrator(id, merchant, secret, softpayTarget);
        // 1: Lambda to create AppSwitch client (minimal) options upon request.
        Callable<ClientOptions> options = () -> {
            // Create options via (overloaded) constructor, or via 'ClientOptions.Builder' from AppSwitch SDK 1.5.1.
            // return new ClientOptions(applicationContext, new Integrator(id, merchant, secretAsChars, new IntegratorEnvironment.JavaEnvironment(), SoftpayTarget.ANY)); // target from 1.5.3, 'ANY' is default
            return new ClientOptions(applicationContext, integrator);
        };

        // 2: Get existing client, or create a Softpay AppSwitch client based on lazily created client options.
        //    From AppSwitch 1.5.5, 'Softpay.clientForTarget' can be used to ensure the integrator credentials match the target.
        client = Softpay.clientOrNew(options); // synchronous
    }

    public void setPrintWidth(int pw) {
        this.pw = pw;
    }

    public boolean abort() {
        if (currentRequest == null) return false;
        return currentRequest.abort(POS_APP_ABORT_CODE, POS_APP_ABORT_REASON);
    }

    public boolean pay(int cents, String callbackfn) {

        if (client == null) {
            Log.e(TAG,"client setup not called!");
            return false;
        }

        // 3: Get relevant manager for relevant use case, here a transaction manager.
        TransactionManager manager = client.getTransactionManager(); // synchronous

        // 4: Define the amount to pay and payment transaction to process in the Softpay app, here 42 EUR (in minor unit).
        Amount amount = new Amount(cents, "EUR");

        PaymentTransaction payment = new PaymentTransaction() {
            @Override @NonNull
            public Amount getAmount() {
                return amount; // immutable instance
            }

            // 7: Mandatory - POS app callback on success.
            @Override
            public void onSuccess(@NonNull Request request, @NonNull Transaction transaction) {
                // Success!
                // ..
                String receipt = Objects.requireNonNull(transaction.getReceipt()).format( pw, true, '-' );
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:" + callbackfn + "(true, ' " + transaction.toOutput(JSON) + " ', '" + receipt + "');   ");
                    }
                });
                Log.e(TAG, "success!");
                currentRequest = null;
            }
            // 7: Optional - POS app callback on failure, can even happen before 'request.process()' (after 5).
            @Override
            public void onFailure(@NonNull Manager<?> manager, @Nullable Request request, @NonNull Failure failure) {
                // Failure!
                // ..
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:" + callbackfn + "(false, ' " + failure.toOutput(JSON) + " ');   ");
                    }
                });

                Log.e(TAG, "failed!");
                currentRequest = null;
            }
        };

        // Optional: POS app defined request code, if so desired.
        long requestCode = 1234;

        // 5: Get request for the payment via 'manager'.
        return manager.requestFor(payment, requestCode, request -> {
            TransactionRequestOptions opts = manager.options(request);
            opts.setReceipt(Tier.LOCAL);
            opts.setSwitchBackTimeout(3000L);
            // 6: Process it in the Softpay app
            request.process();
            currentRequest = request;
        });

    }

}
