package at.spin.webpos;

import static android.content.ContentValues.TAG;

import static io.softpay.client.OutputTypes.JSON;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;
import android.webkit.WebView;

import java.util.concurrent.Callable;

import io.softpay.client.Client;
import io.softpay.client.ClientOptions;
import io.softpay.client.Failure;
import io.softpay.client.Manager;
import io.softpay.client.Request;
import io.softpay.client.Softpay;
import io.softpay.client.SoftpayTarget;
import io.softpay.client.domain.Amount;
import io.softpay.client.domain.Integrator;
import io.softpay.client.domain.IntegratorEnvironment;
import io.softpay.client.domain.Transaction;
import io.softpay.client.transaction.PaymentTransaction;
import io.softpay.client.transaction.TransactionManager;

public class SoftPayImpl {

    final String test;
    final WebView webView;
    Client client;
    public SoftPayImpl(final Context applicationContext, WebView webView) {
        test = "Sepp";
        this.webView = webView;

        // 1: Lambda to create AppSwitch client (minimal) options upon request.
        Callable<ClientOptions> options = () -> {
            String id = "SPAY-spingmbh";
            String merchant = "AcmeSpinGmbH";
            // Assuming your organisation has been provided the secret as "e8337ccce2db45b6be203918944f3fc8".
            // char[] secret = new char[]{'e','8','3','3','7','c','c','c','e','2','d','b','4','5','b','6','b','e','2','0','3','9','1','8','9','4','4','f','3','f','c','8'};
            char[] secret = "f53eef50ec5049088abff4e2edfd0757".toCharArray();

            // Create options via (overloaded) constructor, or via 'ClientOptions.Builder' from AppSwitch SDK 1.5.1.
            return new ClientOptions(applicationContext, new Integrator(id, merchant, secret, new IntegratorEnvironment.JavaEnvironment(), SoftpayTarget.ANY)); // target from 1.5.3, 'ANY' is default
        };

        // 2: Get existing client, or create a Softpay AppSwitch client based on lazily created client options.
        //    From AppSwitch 1.5.5, 'Softpay.clientForTarget' can be used to ensure the integrator credentials match the target.
        client = Softpay.clientOrNew(options); // synchronous

    }

    public void pay(int cents, String callbackfn) {
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

                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:" + callbackfn + "(' " + transaction.toOutput(JSON) + " ');   ");
                    }
                });
                Log.e(TAG, "success!");
            }
            // 7: Optional - POS app callback on failure, can even happen before 'request.process()' (after 5).
            @Override
            public void onFailure(@NonNull Manager<?> manager, @Nullable Request request, @NonNull Failure failure) {
                // Failure!
                // ..
                webView.post(new Runnable() {
                    @Override
                    public void run() {
                        webView.loadUrl("javascript:" + callbackfn + "(' " + failure.toOutput(JSON) + " ');   ");
                    }
                });

                Log.e(TAG, "failed!");
            }
        };

        // Optional: POS app defined request code, if so desired.
        long requestCode = 1234;

        // 5: Get request for the payment via 'manager'.
        manager.requestFor(payment, requestCode, request -> {
            // ..

            // 6: Process it in the Softpay app
            request.process();
        });

    }

    public String getTest() {
        return test;
    }

}
