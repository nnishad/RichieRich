package com.nightowldevelopers.richierich


import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Toast
import com.android.billingclient.api.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.games.AnnotatedData
import com.google.android.gms.games.Games
import com.google.android.gms.games.leaderboard.LeaderboardScore
import com.google.android.gms.games.leaderboard.LeaderboardVariant
import com.google.android.material.snackbar.Snackbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.android.synthetic.main.activity_google.*
import kotlinx.android.synthetic.main.activity_main.products

class MainActivity : BaseActivity(), PurchasesUpdatedListener, View.OnClickListener {

    private lateinit var billingClient: BillingClient
    private lateinit var productsAdapter: ProductsAdapter

    // [START declare_auth]
    private lateinit var auth: FirebaseAuth
    // [END declare_auth]
    protected val RC_LEADERBOARD_UI = 9004
    private val RC_ACHIEVEMENT_UI = 9003


    private lateinit var googleSignInClient: GoogleSignInClient
    var newScore = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_google)
        setupBillingClient()

        products.visibility = View.GONE
        leaderboard.visibility = View.GONE
        achievement.visibility = View.GONE

        // Button listeners
        signInButton.setOnClickListener(this)
        signOutButton.setOnClickListener(this)

        achievement.setOnClickListener { showAchievements() }
        leaderboard.setOnClickListener { showLeaderboard() }


        // [START config_signin]
        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestScopes(Games.SCOPE_GAMES_LITE)
            .requestEmail()
            .build()
        // [END config_signin]

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // [START initialize_auth]
        // Initialize Firebase Auth
        auth = FirebaseAuth.getInstance()
        // [END initialize_auth]

        //autopopup for login on startup
        signIn()

    }

    // [START on_start_check_user]
    public override fun onStart() {
        super.onStart()
        // Check if user is signed in (non-null) and update UI accordingly.
        val currentUser = auth.currentUser
        updateUI(currentUser)
    }
    // [END on_start_check_user]

    // [START onactivityresult]
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        // Result returned from launching the Intent from GoogleSignInApi.getSignInIntent(...);
        if (requestCode == RC_SIGN_IN) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(data)
            try {
                // Google Sign In was successful, authenticate with Firebase
                val account = task.getResult(ApiException::class.java)
                firebaseAuthWithGoogle(account!!)

                var gamesClient = Games.getGamesClient(this@MainActivity, account)
                gamesClient =
                    Games.getGamesClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                gamesClient.setViewForPopups(findViewById(android.R.id.content))
                gamesClient.setGravityForPopups(Gravity.TOP or Gravity.CENTER_HORIZONTAL)
                onLoadProductsClicked()
            } catch (e: ApiException) {
                // Google Sign In failed, update UI appropriately
                Log.w(TAG, "Google sign in failed", e)
                // [START_EXCLUDE]
                updateUI(null)
                // [END_EXCLUDE]
            }
        }
    }
    // [END onactivityresult]

    // [START auth_with_google]
    private fun firebaseAuthWithGoogle(acct: GoogleSignInAccount) {
        Log.d(TAG, "firebaseAuthWithGoogle:" + acct.id!!)
        // [START_EXCLUDE silent]
        showProgressDialog()
        // [END_EXCLUDE]

        val credential = GoogleAuthProvider.getCredential(acct.idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Sign in success, update UI with the signed-in user's information
                    Log.d(TAG, "signInWithCredential:success")
                    val user = auth.currentUser
                    updateUI(user)
                    onLoadProductsClicked()
                } else {
                    // If sign in fails, display a message to the user.
                    Log.w(TAG, "signInWithCredential:failure", task.exception)
                    Snackbar.make(main_layout, "Authentication Failed.", Snackbar.LENGTH_SHORT)
                        .show()
                    updateUI(null)
                }

                // [START_EXCLUDE]
                hideProgressDialog()
                // [END_EXCLUDE]
            }
    }
    // [END auth_with_google]

    // [START signin]
    private fun signIn() {
        val signInIntent = googleSignInClient.signInIntent
        startActivityForResult(signInIntent, RC_SIGN_IN)
    }
    // [END signin]

    private fun signOut() {
        // Firebase sign out
        auth.signOut()

        // Google sign out
        googleSignInClient.signOut().addOnCompleteListener(this) {
            updateUI(null)
            products.visibility = View.GONE
            leaderboard.visibility = View.GONE
            achievement.visibility = View.GONE
        }
    }

    private fun revokeAccess() {
        // Firebase sign out
        auth.signOut()

        // Google revoke access
        googleSignInClient.revokeAccess().addOnCompleteListener(this) {
            updateUI(null)

        }
    }

    private fun updateUI(user: FirebaseUser?) {
        hideProgressDialog()
        if (user != null) {
            // status.text = getString(R.string.google_status_fmt, user.email)
            //detail.text = getString(R.string.firebase_status_fmt, user.uid)
            onLoadProductsClicked()
            signInButton.visibility = View.GONE
            signOutAndDisconnect.visibility = View.VISIBLE
            homeLogo.visibility = View.GONE

            textView4.visibility = View.VISIBLE
            textView3.visibility = View.VISIBLE
            /*rateApp.visibility = View.VISIBLE
            textViewRate.visibility = View.VISIBLE*/
            progressBar.visibility=View.VISIBLE

        } else {
            //status.setText(R.string.signed_out)
            detail.text = null

            homeLogo.visibility = View.VISIBLE
            signInButton.visibility = View.VISIBLE
            signOutAndDisconnect.visibility = View.GONE
            textView4.visibility = View.GONE
            textView3.visibility = View.GONE
            /*rateApp.visibility = View.GONE
            textViewRate.visibility = View.GONE*/

        }
    }


    override fun onClick(v: View) {
        val i = v.id
        when (i) {
            R.id.signInButton -> signIn()
            R.id.signOutButton -> signOut()
            R.id.disconnectButton -> revokeAccess()
        }
    }

    companion object {
        private const val TAG = "GoogleActivity"
        private const val RC_SIGN_IN = 9001
        private val skuList = listOf("test_50","test_100","test_500","test_1000","test_5000")
    }

    private fun setupBillingClient() {
        billingClient = BillingClient
            .newBuilder(this)
            .setListener(this)
            .build()

        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(@BillingClient.BillingResponse billingResponseCode: Int) {
                if (billingResponseCode == BillingClient.BillingResponse.OK) {
                    println("BILLING | startConnection | RESULT OK")
                    clearHistory()
                } else {
                    println("BILLING | startConnection | RESULT: $billingResponseCode")
                }
            }

            override fun onBillingServiceDisconnected() {
                println("BILLING | onBillingServiceDisconnected | DISCONNECTED")
            }
        })
    }

    fun onLoadProductsClicked(view: View) {
        if (billingClient.isReady) {
            val params = SkuDetailsParams
                .newBuilder()
                .setSkusList(skuList)
                .setType(BillingClient.SkuType.INAPP)
                .build()
            billingClient.querySkuDetailsAsync(params) { responseCode, skuDetailsList ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    println("querySkuDetailsAsync, responseCode: $responseCode")
                    clearHistory()
                    initProductAdapter(skuDetailsList)
                } else {
                    println("Can't querySkuDetailsAsync, responseCode: $responseCode")
                }
            }
        } else {
            println("Billing Client not ready")
        }
    }

    fun onLoadProductsClicked() {
        products.visibility = View.VISIBLE
        leaderboard.visibility = View.VISIBLE
        achievement.visibility = View.VISIBLE
        if (billingClient.isReady) {
            val params = SkuDetailsParams
                .newBuilder()
                .setSkusList(skuList)
                .setType(BillingClient.SkuType.INAPP)
                .build()
            billingClient.querySkuDetailsAsync(params) { responseCode, skuDetailsList ->
                if (responseCode == BillingClient.BillingResponse.OK) {
                    println("querySkuDetailsAsync, responseCode: $responseCode")
                    initProductAdapter(skuDetailsList)
                    progressBar.visibility=View.GONE
                } else {
                    println("Can't querySkuDetailsAsync, responseCode: $responseCode")
                }
            }
        } else {
            println("Billing Client not ready")
        }
    }


    private fun initProductAdapter(skuDetailsList: List<SkuDetails>) {
        productsAdapter = ProductsAdapter(skuDetailsList) {
            val billingFlowParams = BillingFlowParams
                .newBuilder()
                .setSkuDetails(it)
                .build()
            billingClient.launchBillingFlow(this, billingFlowParams)
        }
        products.adapter = productsAdapter
    }

    override fun onPurchasesUpdated(responseCode: Int, purchases: MutableList<Purchase>?) {
        println("onPurchasesUpdated: $responseCode")
        Toast.makeText(
            this, "onPurchasesUpdated:$responseCode", Toast.LENGTH_LONG
        )
        allowMultiplePurchases(purchases)
        if (responseCode == 0){
            clearHistory()
            var purchases_data=purchases.toString().split(',',limit = 3).last().split(',').first().split(':').last().replace('"',' ').trim()
            Log.d("DOG",purchases_data)
            Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                //.submitScore(getString(R.string.leaderboard_richness_rank), 150000)
                .loadCurrentPlayerLeaderboardScore(getString(R.string.leaderboard_richness_rank),
                    LeaderboardVariant.TIME_SPAN_ALL_TIME,
                    LeaderboardVariant.COLLECTION_PUBLIC).addOnSuccessListener { leaderboardScoreAnnotatedData: AnnotatedData<LeaderboardScore>? ->

                    var score = 0L
                    if (leaderboardScoreAnnotatedData != null) {
                        if (leaderboardScoreAnnotatedData.get() != null) {
                            score = leaderboardScoreAnnotatedData.get()!!.rawScore
                            Log.d("DOG", "LeaderBoard: " + score)
                            updateRank(score, purchases_data)
                        } else {
                            Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                                .submitScore(getString(R.string.leaderboard_richness_rank), 1)
                            Log.d("DOG", "LeaderBoard: .get() is null")
                            updateRank(1, purchases_data)
                        }
                    } else {
                        Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                            .submitScore(getString(R.string.leaderboard_richness_rank), 1)
                        Log.d("DOG", "LeaderBoard: Data not found")
                        updateRank(1, purchases_data)
                    }
                }




        }
        else {
            //loadProducts.setText("Payment Failed!")
        }

    }

    private fun updateRank(score: Long, purchasesData: String) {
        progressBar.visibility=View.VISIBLE
        //Toast.makeText(this,score.toString()+""+purchasesData,Toast.LENGTH_LONG)
        if(purchasesData=="test_50"){
            setScore(score, 50)
        }
        if(purchasesData=="test_100"){
            setScore(score, 100)
        }
        if(purchasesData=="test_500"){
            setScore(score, 500)
        }
        if(purchasesData=="test_1000"){
            setScore(score, 1000)
        }
        if(purchasesData=="test_5000"){
            setScore(score, 5000)
        }

        if(score>=100){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_100))

        }
        if(score>=500){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_500))

        }
        if(score>=1000){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_1000))

        }
        if(score>=5000){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_5000))

        }
        if(score>=50000){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_50000))

        }

        if(score>=100000){
            Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
                .unlock(getString(R.string.achievement_donation_score_100000))

        }
        progressBar.visibility=View.GONE

    }

    private fun setScore(score: Long, i: Int) {
        var prev = score
        Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
            .submitScore(getString(R.string.leaderboard_richness_rank), score + i)
        var new = getScore()
        Log.d("DOG", "LeaderBoard: " + new + "score" + prev)


    }

    private fun getScore(): Long {
        Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
            .loadCurrentPlayerLeaderboardScore(
                getString(R.string.leaderboard_richness_rank),
                LeaderboardVariant.TIME_SPAN_ALL_TIME,
                LeaderboardVariant.COLLECTION_PUBLIC
            )
            .addOnSuccessListener { leaderboardScoreAnnotatedData: AnnotatedData<LeaderboardScore>? ->

                if (leaderboardScoreAnnotatedData != null) {
                    if (leaderboardScoreAnnotatedData.get() != null) {
                        newScore = leaderboardScoreAnnotatedData.get()!!.rawScore
                        Log.d("DOG", "LeaderBoard: " + newScore)
                    } else {
                        Log.d("DOG", "LeaderBoard: .get() is null")
                    }
                } else {
                    Log.d("DOG", "LeaderBoard: Not found")
                }
            }
        Log.d("DOG", "LeaderBoard: " + newScore)
        return newScore
    }


    private fun allowMultiplePurchases(purchases: MutableList<Purchase>?) {
        val purchase = purchases?.first()
        if (purchase != null) {
            billingClient.consumeAsync(purchase.purchaseToken) { responseCode, purchaseToken ->
                if (responseCode == BillingClient.BillingResponse.OK && purchaseToken != null) {
                    println("AllowMultiplePurchases success, responseCode: $responseCode")
                    Toast.makeText(
                        this, "MultiplePurchase:$responseCode", Toast.LENGTH_LONG
                    )
                } else {
                    println("Can't allowMultiplePurchases, responseCode: $responseCode")
                }
            }
        }
    }

    private fun clearHistory() {
        billingClient.queryPurchases(BillingClient.SkuType.INAPP).purchasesList
            .forEach {
                billingClient.consumeAsync(it.purchaseToken) { responseCode, purchaseToken ->
                    if (responseCode == BillingClient.BillingResponse.OK && purchaseToken != null) {
                        println("onPurchases Updated consumeAsync, purchases token removed: $purchaseToken")
                    } else {
                        println("onPurchases some troubles happened: $responseCode")
                    }
                }
            }
    }

    private fun showLeaderboard() {
        Games.getLeaderboardsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
            .getLeaderboardIntent(getString(R.string.leaderboard_richness_rank))
            .addOnSuccessListener { intent -> startActivityForResult(intent, RC_LEADERBOARD_UI) }
    }


    private fun showAchievements() {
        Games.getAchievementsClient(this, GoogleSignIn.getLastSignedInAccount(this)!!)
            .achievementsIntent
            .addOnSuccessListener { intent -> startActivityForResult(intent, RC_ACHIEVEMENT_UI) }
    }

}