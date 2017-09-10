package org.sorz.lab.tinykeepass.autofill;

import android.app.assist.AssistStructure;
import android.content.IntentSender;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveRequest;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.TextView;

import org.sorz.lab.tinykeepass.KeePassStorage;
import org.sorz.lab.tinykeepass.R;


@RequiresApi(api = Build.VERSION_CODES.O)
public class TinyAutofillService extends AutofillService {
    static private final String TAG = TinyAutofillService.class.getName();
    @Override
    public void onFillRequest(@NonNull FillRequest request,
                              @NonNull CancellationSignal cancellationSignal,
                              @NonNull FillCallback callback) {

        cancellationSignal.setOnCancelListener(() -> Log.d(TAG, "autofill canceled."));

        AssistStructure structure = request.getFillContexts()
                .get(request.getFillContexts().size() - 1).getStructure();
        StructureParser.Result parseResult = new StructureParser(structure).parse();
        if (parseResult.isEmpty()) {
            callback.onFailure(getString(R.string.autofill_not_supported_field_found));
            return;
        }

        FillResponse.Builder responseBuilder = new FillResponse.Builder();
        if (KeePassStorage.get() == null) {
            RemoteViews presentation = getRemoteViews(getString(R.string.autofill_unlock_db),
                    android.R.drawable.ic_lock_lock);
            IntentSender sender = AuthActivity.getAuthIntentSenderForResponse(this);
            responseBuilder.setAuthentication(parseResult.getAutofillIds(), sender, presentation);
        } else {
            // TODO: show matched accounts
            throw new UnsupportedOperationException();
        }
        callback.onSuccess(responseBuilder.build());
    }

    RemoteViews getRemoteViews(String text, @DrawableRes int icon) {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.autofill_service_list_item);
        views.setTextViewText(R.id.textView, text);
        views.setTextViewCompoundDrawables(R.id.textView, icon, 0, 0, 0);
        return views;
    }

    @Override
    public void onSaveRequest(@NonNull SaveRequest request, @NonNull SaveCallback callback) {
        callback.onFailure(getString(R.string.autofill_not_support_save));
    }

    @Override
    public void onConnected() {
        Log.d(TAG, "onConnected");
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "onDisconnected");
    }
}
