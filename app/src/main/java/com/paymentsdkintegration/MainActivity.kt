package com.paymentsdkintegration

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.wallet.AutoResolveHelper
import com.google.android.gms.wallet.contract.TaskResultContracts
import com.paymentsdkintegration.ui.ProductScreen
import com.paymentsdkintegration.ui.theme.PaymentSDKIntegrationTheme
import com.paymentsdkintegration.utilities.googleWalletPayApi.GoogleWalletViewModel
import com.paymentsdkintegration.utilities.googleWalletPayApi.PaymentUiState
import com.samsung.android.sdk.samsungpay.v2.PartnerInfo
import com.samsung.android.sdk.samsungpay.v2.SamsungPay
import com.samsung.android.sdk.samsungpay.v2.SpaySdk
import com.samsung.android.sdk.samsungpay.v2.StatusListener
import com.samsung.android.sdk.samsungpay.v2.payment.CardInfo
import com.samsung.android.sdk.samsungpay.v2.payment.CustomSheetPaymentInfo
import com.samsung.android.sdk.samsungpay.v2.payment.PaymentManager
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountBoxControl
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.AmountConstants
import com.samsung.android.sdk.samsungpay.v2.payment.sheet.CustomSheet

class MainActivity : ComponentActivity() {

    private val mPaymentModel : GoogleWalletViewModel by viewModels()

    private lateinit var partnerInfo: PartnerInfo
    private lateinit var paymentManager: PaymentManager
    private val SERVICE_ID = "0915499788d6493aa3a038"
    private var isSpyReady = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val bundle = Bundle()
        bundle.putString(SpaySdk.PARTNER_SERVICE_TYPE, SpaySdk.ServiceType.INAPP_PAYMENT.toString())
        partnerInfo = PartnerInfo(SERVICE_ID, bundle)

        updateSamsungPayButton()

        setContent {
            PaymentSDKIntegrationTheme {
                val payState: PaymentUiState by mPaymentModel.paymentUiState.collectAsStateWithLifecycle()
                ProductScreen(
                    title = "Men's Tech Shell Full-Zip",
                    description = "A versatile full-zip that you can wear all day long and even...",
                    price = "$1.20",
                    image = R.drawable.ts_10_11019a,
                    payUiState = payState,
                    onGooglePayButtonClick = this::requestPayment,
                    samsungPayReady = isSpyReady,
                    onSamsungPayButtonClick = { startInAppPayWithCustomSheet() }
                )
            }
        }
    }

    private val paymentDataLauncher = registerForActivityResult(TaskResultContracts.GetPaymentDataResult()) { taskResult ->
        when (taskResult.status.statusCode) {
            CommonStatusCodes.SUCCESS -> {
                taskResult.result!!.let {
                    Log.i("Google Pay result:", it.toJson())
                    mPaymentModel.setPaymentData(it)
                }
            }
            CommonStatusCodes.CANCELED -> {
                Log.i("Google Pay result:",
                    "User cancelled the Process Or User does not have setup google pay account.")
            }
            AutoResolveHelper.RESULT_ERROR -> {
                Log.i("Google Pay result:", "The API returned an error(${taskResult.status}: Status)")

            }
            CommonStatusCodes.INTERNAL_ERROR -> {
                Log.i("Google Pay result:", "The API returned an internal error.")

            }
        }
    }

    private fun requestPayment() {
        val task = mPaymentModel.getLoadPaymentDataTask(priceCents = 10L)
        task.addOnCompleteListener(paymentDataLauncher::launch)
    }


    private fun updateSamsungPayButton() {
        val samsungPay = SamsungPay(this, partnerInfo)

        samsungPay.getSamsungPayStatus(object : StatusListener {
            override fun onSuccess(status: Int, bundle: Bundle) {
                when (status) {
                    SpaySdk.SPAY_READY -> {
                        isSpyReady = true
                        Log.i("samsungPay","status : Ready.")
                    }
                    SpaySdk.SPAY_NOT_READY -> {
                        // Samsung Pay is supported but not fully ready.

                        // If EXTRA_ERROR_REASON is ERROR_SPAY_APP_NEED_TO_UPDATE,
                        // Call goToUpdatePage().

                        // If EXTRA_ERROR_REASON is ERROR_SPAY_SETUP_NOT_COMPLETED,
                        // Call activateSamsungPay().
                        Log.i("samsungPay","status : SPY not ready.")
                        val extraError = bundle.getInt(SamsungPay.EXTRA_ERROR_REASON)
                        if (extraError == SamsungPay.ERROR_SPAY_SETUP_NOT_COMPLETED) {
                            doActivateSamsungPay(SpaySdk.ServiceType.INAPP_PAYMENT.toString())
                        }
                    }
                    SpaySdk.SPAY_NOT_ALLOWED_TEMPORALLY -> {
                        // If EXTRA_ERROR_REASON is ERROR_SPAY_CONNECTED_WITH_EXTERNAL_DISPLAY,
                        // guide user to disconnect it.
                        Log.i("samsungPay","status : SPAY_NOT_ALLOWED_TEMPORALLY.")
                    }
                    SpaySdk.SPAY_NOT_SUPPORTED -> {
                        Log.i("samsungPay","status : SPAY_NOT_SUPPORTED.")
                    }

                    else -> Log.i("samsungPay","status : SPAY error.")
                }
            }

            override fun onFail(errorCode: Int, bundle: Bundle) {
                Toast.makeText(applicationContext, "getSamsungPayStatus fail", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun doActivateSamsungPay(serviceType: String) {
        val bundle = Bundle()
        bundle.putString(SamsungPay.PARTNER_SERVICE_TYPE, serviceType)
        val partnerInfo = PartnerInfo(SERVICE_ID, bundle)
        val samsungPay = SamsungPay(this, partnerInfo)
        samsungPay.activateSamsungPay()
    }

    /*
     * Make user's transaction details.
     * The merchant app should send PaymentInfo to Samsung Pay via the applicable Samsung Pay SDK API method for the operation
     * being invoked.
     * Upon successful user authentication, Samsung Pay returns the "Payment Info" structure and the result string.
     * The result string is forwarded to the PG for transaction completion and will vary based on the requirements of the PG used.
     * The code example below illustrates how to populate payment information in each field of the PaymentInfo class.
     */
    private fun makeTransactionDetailsWithSheet(): CustomSheetPaymentInfo? {
        val brandList = brandList

        val extraPaymentInfo = Bundle()
        val customSheet = CustomSheet()

        customSheet.addControl(makeAmountControl())
        return CustomSheetPaymentInfo.Builder()
            .setMerchantId("123456")
            .setMerchantName("Sample Merchant")
            .setOrderNumber("AMZ007MAR")
            // If you want to enter address, please refer to the javaDoc :
            // reference/com/samsung/android/sdk/samsungpay/v2/payment/sheet/AddressControl.html
            .setAddressInPaymentSheet(CustomSheetPaymentInfo.AddressInPaymentSheet.DO_NOT_SHOW)
            .setAllowedCardBrands(brandList)
            .setCardHolderNameEnabled(true)
            .setRecurringEnabled(false)
            .setCustomSheet(customSheet)
            .setExtraPaymentInfo(extraPaymentInfo)
            .build()
    }

    private fun makeAmountControl(): AmountBoxControl {
        val amountBoxControl = AmountBoxControl("AMOUNT_CONTROL_ID", "INR")
        amountBoxControl.addItem("PRODUCT_ITEM_ID", "Item", 1199.00, "")
        amountBoxControl.addItem("PRODUCT_TAX_ID", "Tax", 5.0, "")
        amountBoxControl.addItem("PRODUCT_SHIPPING_ID", "Shipping", 1.0, "")
        amountBoxControl.setAmountTotal(1205.00, AmountConstants.FORMAT_TOTAL_PRICE_ONLY)
        return amountBoxControl
    }

    private val brandList: ArrayList<SpaySdk.Brand>
        get() {
            val brandList = ArrayList<SpaySdk.Brand>()
            brandList.add(SpaySdk.Brand.VISA)
            brandList.add(SpaySdk.Brand.MASTERCARD)
            brandList.add(SpaySdk.Brand.AMERICANEXPRESS)
            brandList.add(SpaySdk.Brand.DISCOVER)

            return brandList
        }

    /*
 * PaymentManager.startInAppPayWithCustomSheet is a method to request online(in-app) payment with Samsung Pay.
 * Partner app can use this method to make in-app purchase using Samsung Pay from their
 * application with custom payment sheet.
 */
    private fun startInAppPayWithCustomSheet() {
        paymentManager = PaymentManager(applicationContext, partnerInfo)

        paymentManager.startInAppPayWithCustomSheet(
            makeTransactionDetailsWithSheet(),
            transactionInfoListener
        )
    }

    /*
     * CustomSheetTransactionInfoListener is for listening callback events of online (in-app) custom sheet payment.
     * This is invoked when card is changed by the user on the custom payment sheet,
     * and also with the success or failure of online (in-app) payment.
     */
    private val transactionInfoListener: PaymentManager.CustomSheetTransactionInfoListener =
        object : PaymentManager.CustomSheetTransactionInfoListener {
            // This callback is received when the user changes card on the custom payment sheet in Samsung Pay.
            override fun onCardInfoUpdated(selectedCardInfo: CardInfo, customSheet: CustomSheet) {
                /*
                 * Called when the user changes card in Samsung Pay.
                 * Newly selected cardInfo is passed and partner app can update transaction amount based on new card (if needed).
                 * Call updateSheet() method. This is mandatory.
                 */
                paymentManager.updateSheet(customSheet)
            }

            override fun onSuccess(
                response: CustomSheetPaymentInfo,
                paymentCredential: String,
                extraPaymentData: Bundle
            ) {
                /*
                 * You will receive the payloads shown below in paymentCredential parameter
                 * The output paymentCredential structure varies depending on the PG you're using and the integration model (direct, indirect) with Samsung.
                 */
                Toast.makeText(applicationContext, "onSuccess() ", Toast.LENGTH_SHORT).show()
            }

            // This callback is received when the online payment transaction has failed.
            override fun onFailure(errorCode: Int, errorData: Bundle?) {
                Toast.makeText(applicationContext, "onFailure() ", Toast.LENGTH_SHORT).show()
            }
        }

}



