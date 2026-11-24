package com.burdew.app.ui.requests;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.burdew.app.R;
import com.burdew.app.core.Money;
import com.burdew.app.core.PaymentStatus;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.core.TransactionRules;
import com.burdew.app.data.db.Entities.AuditLog;
import com.burdew.app.data.db.Entities.Receipt;
import com.burdew.app.data.db.Entities.ServiceRequest;
import com.burdew.app.data.db.Entities.Transaction;
import com.burdew.app.data.db.Entities.User;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.map.MapActivity;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * One request end-to-end: status changes, assignment, the payment record,
 * receipt photos and verification, plus its audit trail (spec screens 10-12).
 */
public class RequestDetailActivity extends BaseActivity {

    public static final String EXTRA_ID = "requestId";
    private static final int TAKE_PHOTO = 7101;
    private static final PaymentStatus[] EDITABLE = {PaymentStatus.NOT_COLLECTED, PaymentStatus.COLLECTED,
            PaymentStatus.SUBMITTED, PaymentStatus.REFUNDED};

    private String requestId;
    private ServiceRequest request;
    private Transaction tx;
    private LinearLayout info, actions, payment, receipts, history;
    private EditText bill, collected, paid, charge, receiptRef, notes;
    private Spinner payStatus;
    private TextView payInfo;
    private File pendingPhoto;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        requestId = getIntent().getStringExtra(EXTRA_ID);
        if (requestId == null) { finish(); return; }
        if (savedInstanceState != null && savedInstanceState.getString("photo") != null) {
            pendingPhoto = new File(savedInstanceState.getString("photo"));
        }
        setTitle(R.string.request);
        LinearLayout f = Ui.form(this);
        info = Ui.card(this, f);
        actions = Ui.column(this);
        f.addView(actions);

        payment = Ui.column(this);
        f.addView(payment);
        buildPaymentForm();

        Ui.section(f, R.string.receipts);
        receipts = Ui.column(this);
        f.addView(receipts);

        Ui.section(f, R.string.history);
        history = Ui.column(this);
        f.addView(history);

        repo().db().requests().observe(requestId).observe(this, r -> {
            if (r == null) return;
            request = r;
            renderInfo();
            renderActions();
            renderPaymentVisibility();
        });
        repo().db().transactions().observeForRequest(requestId).observe(this, t -> {
            boolean first = tx == null && t != null;
            tx = t;
            if (first || t == null || t.paymentStatus == PaymentStatus.VERIFIED) fillPayment();
            renderPaymentVisibility();
            renderActions();
            if (t != null) observeReceipts(t.transactionId);
        });
        repo().db().audit().forEntity("ServiceRequest", requestId).observe(this, this::renderHistory);
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        if (pendingPhoto != null) out.putString("photo", pendingPhoto.getAbsolutePath());
    }

    // ---------------------------------------------------------------- info

    private void renderInfo() {
        info.removeAllViews();
        TextView code = Ui.text(info, request.requestCode + " · " + Ui.label(request.requestStatus));
        code.setTextSize(18);
        code.setTypeface(code.getTypeface(), android.graphics.Typeface.BOLD);
        Ui.text(info, getString(R.string.service_type) + ": " + Ui.label(request.serviceType));
        Ui.text(info, getString(R.string.account_reference) + ": " + nz(request.accountReference));
        Ui.text(info, getString(R.string.address) + ": " + nz(request.address));
        if (request.description != null) Ui.text(info, getString(R.string.additional_instructions) + ": " + request.description);
        Ui.muted(info, getString(R.string.created_at, Ui.dateTime(request.createdAt)) + " · " + Ui.label(request.source));
        TextView cust = Ui.text(info, "…");
        TextView prov = Ui.muted(info, "");
        repo().getCustomer(request.customerId, r -> {
            if (r.ok() && r.value != null) {
                cust.setText(getString(R.string.customer) + ": " + r.value.fullName + " · " + r.value.phoneNumber);
                cust.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + r.value.phoneNumber))));
            }
        });
        if (request.assignedProviderId != null) {
            repo().getUser(request.assignedProviderId, r -> {
                if (r.ok() && r.value != null) prov.setText(getString(R.string.provider) + ": " + r.value.fullName);
            });
        } else prov.setText(R.string.unassigned);
        if (request.latitude != null) {
            Ui.button(info, R.string.show_on_map, true, v -> startActivity(new Intent(this, MapActivity.class)
                    .putExtra(MapActivity.EXTRA_LAT, request.latitude).putExtra(MapActivity.EXTRA_LON, request.longitude)));
        }
    }

    private static String nz(String s) { return s == null ? "—" : s; }

    // ---------------------------------------------------------------- actions

    private void renderActions() {
        if (request == null) return;
        actions.removeAllViews();
        boolean admin = session().isAdmin();
        boolean mine = session().userId() != null && session().userId().equals(request.assignedProviderId);
        if (admin && (request.requestStatus == RequestStatus.NEW || request.requestStatus == RequestStatus.PENDING
                || request.requestStatus == RequestStatus.ASSIGNED)) {
            Ui.button(actions, request.assignedProviderId == null ? R.string.assign_provider : R.string.reassign_provider,
                    false, v -> chooseProvider());
        }
        for (RequestStatus next : request.requestStatus.next()) {
            if (next == RequestStatus.ASSIGNED) continue; // done through the assign dialog
            if (!request.requestStatus.canMoveTo(next, session().role(), mine)) continue;
            boolean danger = next == RequestStatus.CANCELLED || next == RequestStatus.FAILED;
            Ui.button(actions, actionLabel(next), danger, v -> confirmStatus(next));
        }
    }

    private int actionLabel(RequestStatus s) {
        switch (s) {
            case PENDING: return R.string.action_pending;
            case IN_PROGRESS: return R.string.action_start;
            case PAYMENT_COMPLETED: return R.string.action_payment_completed;
            case RECEIPT_DELIVERED: return R.string.action_receipt_delivered;
            case FAILED: return R.string.action_failed;
            case CANCELLED: return R.string.action_cancel;
            default: return R.string.update_status;
        }
    }

    private void confirmStatus(RequestStatus next) {
        new AlertDialog.Builder(this)
                .setMessage(getString(R.string.confirm_status, Ui.label(next)))
                .setPositiveButton(android.R.string.ok, (d, w) -> repo().changeStatus(requestId, next, r -> {
                    if (!failed(r)) Ui.toast(this, getString(R.string.status_updated));
                }))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void chooseProvider() {
        repo().activeProviders(r -> {
            if (failed(r)) return;
            List<User> ps = r.value;
            if (ps.isEmpty()) { Ui.toast(this, getString(R.string.no_active_providers)); return; }
            String[] names = new String[ps.size()];
            for (int i = 0; i < ps.size(); i++) names[i] = ps.get(i).fullName;
            new AlertDialog.Builder(this).setTitle(R.string.assign_provider)
                    .setItems(names, (d, which) -> repo().assign(requestId, ps.get(which).userId, a -> {
                        if (!failed(a)) Ui.toast(this, getString(R.string.status_updated));
                    })).show();
        });
    }

    // ---------------------------------------------------------------- payment

    private void buildPaymentForm() {
        Ui.section(payment, R.string.payment);
        Ui.muted(payment, getString(R.string.payment_hint));
        bill = Ui.field(payment, R.string.bill_amount, Ui.MONEY);
        collected = Ui.field(payment, R.string.amount_collected, Ui.MONEY);
        paid = Ui.field(payment, R.string.amount_paid, Ui.MONEY);
        charge = Ui.field(payment, R.string.service_charge, Ui.MONEY);
        receiptRef = Ui.field(payment, R.string.receipt_reference, Ui.PLAIN);
        notes = Ui.field(payment, R.string.notes, Ui.MULTILINE);
        String[] labels = new String[EDITABLE.length];
        for (int i = 0; i < EDITABLE.length; i++) labels[i] = Ui.label(EDITABLE[i]);
        payStatus = Ui.spinner(payment, R.string.payment_status, labels);
        payInfo = Ui.muted(payment, "");
        Ui.button(payment, R.string.save_payment, false, v -> savePayment());
        Ui.button(payment, R.string.take_receipt_photo, true, v -> takePhoto());
        Ui.button(payment, R.string.verify_payment, true, v -> repo().verifyPayment(requestId, r -> {
            if (!failed(r)) Ui.toast(this, getString(R.string.payment_verified));
        }));
        if (session().isAdmin()) Ui.button(payment, R.string.void_transaction, true, v -> voidTx());
    }

    private void renderPaymentVisibility() {
        if (request == null) return;
        boolean show = tx != null || request.requestStatus == RequestStatus.IN_PROGRESS;
        payment.setVisibility(show ? View.VISIBLE : View.GONE);
        boolean locked = tx != null && (tx.paymentStatus == PaymentStatus.VERIFIED || tx.voided) && !session().isAdmin();
        for (EditText e : new EditText[]{bill, collected, paid, charge, receiptRef, notes}) e.setEnabled(!locked);
        payStatus.setEnabled(!locked);
        if (tx == null) { payInfo.setText(""); return; }
        long owedTo = TransactionRules.balanceOwedToCustomer(tx.amountCollected, tx.amountPaid, tx.serviceCharge);
        long owedBy = TransactionRules.balanceOwedByCustomer(tx.amountCollected, tx.amountPaid, tx.serviceCharge);
        StringBuilder sb = new StringBuilder(getString(R.string.payment_state, Ui.label(tx.paymentStatus)));
        if (owedTo > 0) sb.append("\n").append(getString(R.string.change_due, Money.format(owedTo)));
        if (owedBy > 0) sb.append("\n").append(getString(R.string.customer_owes, Money.format(owedBy)));
        if (tx.paymentStatus == PaymentStatus.VERIFIED) sb.append("\n").append(getString(R.string.provider_fee_line, Money.format(tx.providerFee)));
        if (tx.voided) sb.append("\n").append(getString(R.string.voided_line, nz(tx.voidReason)));
        payInfo.setText(sb);
    }

    private void fillPayment() {
        if (tx == null) return;
        bill.setText(Money.plain(tx.billAmount));
        collected.setText(Money.plain(tx.amountCollected));
        paid.setText(Money.plain(tx.amountPaid));
        charge.setText(Money.plain(tx.serviceCharge));
        receiptRef.setText(tx.receiptReference);
        notes.setText(tx.notes);
        for (int i = 0; i < EDITABLE.length; i++) if (EDITABLE[i] == tx.paymentStatus) payStatus.setSelection(i);
    }

    private long amount(EditText e) {
        String v = Ui.val(e);
        if (v.isEmpty()) return 0;
        Long m = Money.parse(v);
        if (m == null) throw new IllegalArgumentException(getString(R.string.invalid_amount));
        return m;
    }

    private void savePayment() {
        long b, c, p, s;
        try {
            b = amount(bill); c = amount(collected); p = amount(paid); s = amount(charge);
        } catch (IllegalArgumentException e) {
            Ui.toast(this, e.getMessage());
            return;
        }
        PaymentStatus st = EDITABLE[payStatus.getSelectedItemPosition()];
        repo().saveTransaction(requestId, b, c, p, s, st, Ui.val(receiptRef), Ui.val(notes), r -> {
            if (!failed(r)) Ui.toast(this, getString(R.string.saved));
        });
    }

    private void voidTx() {
        EditText reason = new EditText(this);
        reason.setHint(R.string.void_reason);
        new AlertDialog.Builder(this).setTitle(R.string.void_transaction).setView(reason)
                .setPositiveButton(R.string.void_transaction, (d, w) -> repo().voidTransaction(requestId, Ui.val(reason), r -> {
                    if (!failed(r)) Ui.toast(this, getString(R.string.saved));
                }))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    // ---------------------------------------------------------------- receipts

    private void takePhoto() {
        if (tx == null) { Ui.toast(this, getString(R.string.save_payment_first)); return; }
        pendingPhoto = ReceiptPhotos.newFile(this, tx.transactionId);
        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                .putExtra(MediaStore.EXTRA_OUTPUT, ReceiptPhotos.uriFor(this, pendingPhoto))
                .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if (i.resolveActivity(getPackageManager()) == null) {
            Ui.toast(this, getString(R.string.no_camera_app));
            return;
        }
        startActivityForResult(i, TAKE_PHOTO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != TAKE_PHOTO) return;
        File photo = pendingPhoto;
        pendingPhoto = null;
        if (resultCode != RESULT_OK || photo == null || tx == null) {
            if (photo != null) //noinspection ResultOfMethodCallIgnored
                photo.delete();
            return;
        }
        String txId = tx.transactionId;
        String ref = Ui.val(receiptRef);
        repo().runInBackground(() -> {
            boolean ok = ReceiptPhotos.compress(photo);
            repo().postMain(() -> {
                if (!ok) { Ui.toast(this, getString(R.string.photo_failed)); return; }
                repo().addReceipt(txId, photo.getAbsolutePath(), ref.isEmpty() ? null : ref, r -> {
                    if (!failed(r)) Ui.toast(this, getString(R.string.receipt_saved));
                });
            });
        });
    }

    private androidx.lifecycle.LiveData<List<Receipt>> receiptSource;

    private void observeReceipts(String txId) {
        if (receiptSource != null) return;
        receiptSource = repo().db().receipts().forTransaction(txId);
        receiptSource.observe(this, list -> {
            receipts.removeAllViews();
            if (list.isEmpty()) { Ui.muted(receipts, getString(R.string.no_receipts)); return; }
            for (Receipt rc : list) {
                Ui.muted(receipts, Ui.dateTime(rc.capturedAt) + (rc.receiptReference == null ? "" : " · " + rc.receiptReference));
                if (rc.imagePath != null && new File(rc.imagePath).exists()) {
                    ImageView img = new ImageView(this);
                    img.setAdjustViewBounds(true);
                    img.setMaxHeight(Ui.dp(this, 220));
                    img.setScaleType(ImageView.ScaleType.FIT_START);
                    android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                    o.inSampleSize = 4; // thumbnail only
                    img.setImageBitmap(android.graphics.BitmapFactory.decodeFile(rc.imagePath, o));
                    File file = new File(rc.imagePath);
                    img.setOnClickListener(v -> startActivity(new Intent(Intent.ACTION_VIEW)
                            .setDataAndType(ReceiptPhotos.uriFor(this, file), "image/jpeg")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)));
                    receipts.addView(img);
                }
            }
        });
    }

    // ---------------------------------------------------------------- history

    private void renderHistory(List<AuditLog> logs) {
        history.removeAllViews();
        for (AuditLog l : logs) {
            String change = l.previousValue == null ? nz(l.newValue) : l.previousValue + " → " + nz(l.newValue);
            Ui.muted(history, String.format(Locale.ROOT, "%s · %s · %s", Ui.dateTime(l.timestamp), Ui.label(l.action), change));
        }
    }
}
