package com.example.ui


import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.cardview.widget.CardView
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.recyclerview.widget.RecyclerView
import com.example.data.*
import com.example.security.SecurityUtils
import com.example.service.CurrencyClient
import com.example.service.GeminiClient
import com.example.viewmodel.FinoraaxViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ============================================================================
// 1. COLORS, TYPOGRAPHY & THEMES
// ============================================================================

val ObsidianBackground = Color(0xFF0C0E10)
val ObsidianCard = Color(0xFF181B1F)
val GoldPrimary = Color(0xFFD4AF37)
val GoldSecondary = Color(0xFFC5A059)
val WarmText = Color(0xFFF5F5F2)
val MutedText = Color(0xFF8E929A)
val JadeGreen = Color(0xFF10B981)
val RubyRed = Color(0xFFEF4444)

// Keep the old aliases to prevent compile errors
val SageGreen = GoldPrimary
val SoftSage = GoldSecondary
val SageBackground = ObsidianBackground
val SageWhiteCard = ObsidianCard
val YellowAccent = GoldPrimary
val AlertRed = RubyRed
val SagePrimaryText = WarmText
val SageSecondaryText = MutedText

// Dark version alternatives for contrast depth
val DarkSageGreen = GoldPrimary
val DarkSageBackground = ObsidianBackground
val DarkSageCard = ObsidianCard
val DarkSageText = WarmText
val DarkSageTextSecondary = MutedText

// Set of Material typography styles to start with
val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    )
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkSageGreen,
    onPrimary = SageBackground,
    secondary = SoftSage,
    onSecondary = DarkSageText,
    background = DarkSageBackground,
    onBackground = DarkSageText,
    surface = DarkSageCard,
    onSurface = DarkSageText,
    error = AlertRed,
    onError = SagePrimaryText,
    outline = DarkSageTextSecondary
)

private val LightColorScheme = lightColorScheme(
    primary = SageGreen,
    onPrimary = SageWhiteCard,
    secondary = SoftSage,
    onSecondary = SagePrimaryText,
    background = SageBackground,
    onBackground = SagePrimaryText,
    surface = SageWhiteCard,
    onSurface = SagePrimaryText,
    error = AlertRed,
    onError = SageWhiteCard,
    outline = SageSecondaryText
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

// ============================================================================
// 2. RECYCLERVIEW ADAPTER FOR CHAT
// ============================================================================

class ChatAdapter(private var messages: List<ChatMessage> = emptyList()) :
    RecyclerView.Adapter<ChatAdapter.ChatViewHolder>() {

    fun updateMessages(newMessages: List<ChatMessage>) {
        messages = newMessages
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int {
        return if (messages[position].role == "user") 0 else 1
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChatViewHolder {
        val context = parent.context
        
        // Root container
        val rootLayout = LinearLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            orientation = LinearLayout.VERTICAL
            val density = context.resources.displayMetrics.density
            val pad = (8 * density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        // Card container for bubble
        val cardView = CardView(context).apply {
            radius = 16f * context.resources.displayMetrics.density
            useCompatPadding = true
            elevation = 2f * context.resources.displayMetrics.density
        }

        // Layout inside Card
        val bubbleLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val density = context.resources.displayMetrics.density
            val padHoriz = (12 * density).toInt()
            val padVert = (8 * density).toInt()
            setPadding(padHoriz, padVert, padHoriz, padVert)
        }

        // Message text
        val messageText = TextView(context).apply {
            textSize = 14f
            setTextColor(0xFF1B1B1B.toInt()) // Dark text
        }

        // Time text
        val timeText = TextView(context).apply {
            textSize = 10f
            setTextColor(0xFF757575.toInt()) // Muted text
            val density = context.resources.displayMetrics.density
            setPadding(0, (4 * density).toInt(), 0, 0)
        }

        bubbleLayout.addView(messageText)
        bubbleLayout.addView(timeText)
        cardView.addView(bubbleLayout)
        rootLayout.addView(cardView)

        return ChatViewHolder(rootLayout, cardView, messageText, timeText)
    }

    override fun onBindViewHolder(holder: ChatViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg)
    }

    override fun getItemCount(): Int = messages.size

    class ChatViewHolder(
        val rootView: LinearLayout,
        val cardView: CardView,
        val messageText: TextView,
        val timeText: TextView
    ) : RecyclerView.ViewHolder(rootView) {

        fun bind(message: ChatMessage) {
            messageText.text = message.content
            
            val sdf = SimpleDateFormat("hh:mm a", Locale.getDefault())
            timeText.text = sdf.format(Date(message.timestamp))

            val context = rootView.context
            val density = context.resources.displayMetrics.density
            
            // Align bubble depending on sender
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                if (message.role == "user") {
                    gravity = Gravity.END
                    setMargins((64 * density).toInt(), 0, 0, 0)
                } else {
                    gravity = Gravity.START
                    setMargins(0, 0, (64 * density).toInt(), 0)
                }
            }
            cardView.layoutParams = params

            // Stylize bubble color
            if (message.role == "user") {
                cardView.setCardBackgroundColor(0xFFD4AF37.toInt()) // Gold primary
                messageText.setTextColor(0xFF0F0F0F.toInt()) // Dark text
            } else {
                cardView.setCardBackgroundColor(0xFF1E1E1E.toInt()) // Obsidian Background
                messageText.setTextColor(0xFFFAFAFA.toInt()) // Warm Text
            }
        }
    }
}

// ============================================================================
// 3. ONBOARDING & LOGIN SCREENS
// ============================================================================

@Composable
fun FinoraaxOnboardingContainer(
    user: UserEntity?,
    onOnboardCompleted: (name: String, email: String, pin: String) -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    var step by remember { mutableStateOf(0) } // Step 0: Splash, 1: Privacy Onboarding, 2: Leak Detector, 3: AI Advisor, 4: Login Screen, 5: Create Account (Register)
    
    // Auto-advance if already logged in / onboarded
    LaunchedEffect(user) {
        if (user != null && user.sessionToken != null) {
            onLoginSuccess()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SageBackground)
    ) {
        when (step) {
            0 -> SplashScreen { step = 1 }
            1 -> PrivacyOnboarding { step = 2 }
            2 -> LeakDetectorOnboarding { step = 3 }
            3 -> AdvisorOnboarding { step = 4 }
            4 -> VaultLoginScreen(
                user = user,
                onLoginSuccess = onLoginSuccess,
                onCreateAccountClick = { step = 5 }
            )
            5 -> RegisterScreen(onOnboardCompleted = { name, email, pin ->
                onOnboardCompleted(name, email, pin)
            })
        }
    }
}

@Composable
fun SplashScreen(onNext: () -> Unit) {
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(1800)
        onNext()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(SageGreen, ObsidianBackground)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SageWhiteCard),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Finoraax Logo",
                    tint = SageGreen,
                    modifier = Modifier.size(54.dp)
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Finoraax",
                style = MaterialTheme.typography.headlineLarge,
                color = SageWhiteCard,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Text(
                text = "Privacy-First Wealth Intelligence",
                style = MaterialTheme.typography.bodyMedium,
                color = SoftSage,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun PrivacyOnboarding(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = SageGreen,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "100% Privacy First",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SagePrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Traditional financial trackers sell your private logs and bank passwords. Finoraax is engineered with a secure, privacy-first infrastructure. Your transaction logs, budgets, and investments are processed with real-time online capability, with state-of-the-art encryption guarding your experience.",
                style = MaterialTheme.typography.bodyLarge,
                color = SageSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("privacy_next_btn")
        ) {
            Text("I Value My Privacy", color = SageWhiteCard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun LeakDetectorOnboarding(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = YellowAccent,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Subscription Leak Detector",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SagePrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Forgotten web services, gym plans, and expired trials cost the average person hundreds of dollars in automated leaks.\n\nFinoraax automatically reviews recurring bank charges with local processing, scores your subscription health, and prompts you to decommission toxic autopays immediately.",
                style = MaterialTheme.typography.bodyLarge,
                color = SageSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("leak_next_btn")
        ) {
            Text("Resolve Sub Leaks", color = SageWhiteCard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AdvisorOnboarding(onNext: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Face,
                contentDescription = null,
                tint = SageGreen,
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Smart Financial Advisor",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = SagePrimaryText,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Finoraax embeds a secure Gemini AI advisor that helps you navigate daily questions without logging out.\n\nFrom spending audits and custom emergency plans to micro-budget caps, receive premium coaching entirely tailored to your actual financial goals.",
                style = MaterialTheme.typography.bodyLarge,
                color = SageSecondaryText,
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }

        Button(
            onClick = onNext,
            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("advisor_next_btn")
        ) {
            Text("Unlock Active AI Advisor", color = SageWhiteCard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(onOnboardCompleted: (name: String, email: String, pin: String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Local Account",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SagePrimaryText
        )
        Text(
            text = "Your details are encrypted on-device",
            style = MaterialTheme.typography.bodyMedium,
            color = SageSecondaryText
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Display Name") },
                    leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth().testTag("name_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("email_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    label = { Text("Security PIN (4 digits)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("pin_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )
            }
        }

        if (errorMsg.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = errorMsg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = {
                if (name.isBlank() || email.isBlank() || pin.length != 4) {
                    errorMsg = "Please fill completely. PIN must be 4 digits."
                } else {
                    onOnboardCompleted(name, email, pin)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("register_btn")
        ) {
            Text("Register Secure Vault", color = SageWhiteCard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultLoginScreen(
    user: UserEntity?,
    onLoginSuccess: () -> Unit,
    onCreateAccountClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var failedAttempts by remember { mutableStateOf(0) }
    var lockoutTimeLeft by remember { mutableStateOf(0) }

    LaunchedEffect(lockoutTimeLeft) {
        if (lockoutTimeLeft > 0) {
            kotlinx.coroutines.delay(1000)
            lockoutTimeLeft -= 1
        }
    }

    val displayedError = if (lockoutTimeLeft > 0) {
        "Too many failed attempts. Locked out for $lockoutTimeLeft seconds."
    } else {
        errorMsg
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState())
            .imePadding(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = SageGreen,
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Decrypt Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = SagePrimaryText
        )
        Text(
            text = if (user != null) "Unlock your encrypted local database" else "No active vault detected on this device",
            style = MaterialTheme.typography.bodyMedium,
            color = SageSecondaryText,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(20.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    enabled = lockoutTimeLeft == 0,
                    label = { Text("Email Address") },
                    leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth().testTag("login_email_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 4) pin = it },
                    enabled = lockoutTimeLeft == 0,
                    label = { Text("Security PIN (4 digits)") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("login_pin_input"),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )
            }
        }

        if (displayedError.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = displayedError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                if (user == null) {
                    errorMsg = "No local vault exists. Please create an account first."
                } else if (email.trim().lowercase() != user.email.trim().lowercase()) {
                    errorMsg = "Incorrect email address."
                    failedAttempts++
                    if (failedAttempts >= 5) {
                        lockoutTimeLeft = 30
                    }
                } else if (SecurityUtils.hashPin(pin) != user.pinHash) {
                    failedAttempts++
                    if (failedAttempts >= 5) {
                        lockoutTimeLeft = 30
                    } else {
                        val attemptsLeft = 5 - failedAttempts
                        errorMsg = "Incorrect PIN. $attemptsLeft attempts remaining."
                    }
                } else {
                    errorMsg = ""
                    failedAttempts = 0
                    onLoginSuccess()
                }
            },
            enabled = lockoutTimeLeft == 0,
            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("login_submit_btn")
        ) {
            Text("Decrypt Vault", color = SageWhiteCard, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = onCreateAccountClick,
            modifier = Modifier.testTag("go_to_create_account_btn")
        ) {
            Text("Don't have a vault? Create Account", color = SageGreen, fontWeight = FontWeight.Bold)
        }
    }
}

// ============================================================================
// 4. MAIN APP SCREEN & SUB-TABS
// ============================================================================

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainAppScreen(
    viewModel: FinoraaxViewModel,
    onLogout: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Collect Room state triggers
    val user by viewModel.userState.collectAsState()
    val transactions by viewModel.transactionsState.collectAsState()
    val budgets by viewModel.budgetsState.collectAsState()
    val savingsGoals by viewModel.savingsGoalsState.collectAsState()
    val bills by viewModel.billsState.collectAsState()
    val subscriptions by viewModel.subscriptionsState.collectAsState()
    val investments by viewModel.investmentsState.collectAsState()
    val notifications by viewModel.notificationsState.collectAsState()
    val subHealthScore by viewModel.subHealthScoreState.collectAsState()
    val chatHistory by viewModel.chatHistory.collectAsState()
    val isAnalyzing by viewModel.isAnalyzing.collectAsState()
    val advisorAdvice by viewModel.advisorAdvice.collectAsState()

    var activeTab by remember { mutableStateOf(0) } // 0: Overview, 1: Ledger, 2: Budgets, 3: Calendar, 4: Leak Detector, 5: AI Advisor, 6: Wealth/Settings
    
    // Quick ADD states
    var showAddTxDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = SageGreen, modifier = Modifier.size(28.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Finoraax", fontWeight = FontWeight.Bold, color = SagePrimaryText, letterSpacing = 1.sp)
                    }
                },
                actions = {
                    Box {
                        var showNotificationsMenu by remember { mutableStateOf(false) }
                        IconButton(onClick = { showNotificationsMenu = !showNotificationsMenu }) {
                            Box {
                                Icon(Icons.Default.Notifications, contentDescription = "Alerts", tint = SageSecondaryText)
                                val unread = notifications.size
                                if (unread > 0) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .background(AlertRed, CircleShape)
                                    )
                                }
                            }
                        }
                        DropdownMenu(
                            expanded = showNotificationsMenu,
                            onDismissRequest = { showNotificationsMenu = false },
                            modifier = Modifier.background(SageWhiteCard).width(280.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Finance Protection Notifications", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SagePrimaryText)
                                Text(
                                    "Clear", 
                                    color = AlertRed, 
                                    fontSize = 11.sp, 
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { viewModel.clearAllNotifications() }
                                )
                            }
                            HorizontalDivider()
                            if (notifications.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text("No active fraud alerts or budget caps exceeded.", fontSize = 12.sp, color = SageSecondaryText) },
                                    onClick = {}
                                )
                            } else {
                                notifications.forEach { alert ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(alert.title, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SagePrimaryText)
                                                Text(alert.message, fontSize = 11.sp, color = SageSecondaryText)
                                            }
                                        },
                                        onClick = {}
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Log out local key", tint = SageSecondaryText)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = SageBackground)
            )
        },
        bottomBar = {
            Column {
                NavigationBar(
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
                ) {
                    val items = listOf(
                        Triple("Overview", Icons.Default.Home, 0),
                        Triple("Ledger", Icons.AutoMirrored.Filled.List, 1),
                        Triple("Budgets", Icons.Default.Star, 2),
                        Triple("Calendar", Icons.Default.DateRange, 3),
                        Triple("Leak Audit", Icons.Default.Warning, 4),
                        Triple("Advisor", Icons.Default.Face, 5),
                        Triple("Vault", Icons.Default.Settings, 6)
                    )
                    items.forEach { (label, icon, index) ->
                        NavigationBarItem(
                            selected = activeTab == index,
                            onClick = { activeTab = index },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 10.sp) },
                            alwaysShowLabel = false,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = GoldPrimary,
                                selectedTextColor = GoldPrimary,
                                unselectedIconColor = MutedText,
                                unselectedTextColor = MutedText,
                                indicatorColor = GoldPrimary.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("tab_item_$label")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(SageBackground)
        ) {
            when (activeTab) {
                0 -> OverviewTab(
                    viewModel = viewModel,
                    user = user,
                    transactions = transactions,
                    subHealthScore = subHealthScore,
                    savingsGoals = savingsGoals,
                    onAddTxClick = { showAddTxDialog = true },
                    onAddGoalClick = { showAddGoalDialog = true }
                )
                1 -> LedgerTab(
                    viewModel = viewModel,
                    transactions = transactions
                )
                2 -> BudgetsAndGoalsTab(
                    viewModel = viewModel,
                    budgets = budgets,
                    goals = savingsGoals,
                    onAddGoal = { showAddGoalDialog = true }
                )
                3 -> CashFlowCalendarTab(
                    viewModel = viewModel,
                    transactions = transactions,
                    bills = bills
                )
                4 -> SubscriptionLeakDetectorTab(
                    viewModel = viewModel,
                    subscriptions = subscriptions,
                    subHealthScore = subHealthScore
                )
                5 -> SmartFinancialAdvisorTab(
                    viewModel = viewModel,
                    chatHistory = chatHistory,
                    isAnalyzing = isAnalyzing,
                    advisorAdvice = advisorAdvice
                )
                6 -> InvestmentsAndSettingsTab(
                    viewModel = viewModel,
                    investments = investments,
                    user = user,
                    onLogout = onLogout
                )
            }

            // Quick add transactions modal
            if (showAddTxDialog) {
                AddTransactionDialog(
                    onDismiss = { showAddTxDialog = false },
                    onSave = { type, cat, amt, date, note ->
                        viewModel.addTransaction(type, cat, amt, date, note)
                        showAddTxDialog = false
                    }
                )
            }

            // Quick add Savings Goals modal
            if (showAddGoalDialog) {
                AddSavingsGoalDialog(
                    onDismiss = { showAddGoalDialog = false },
                    onSave = { name, target, current, date, isEmergency ->
                        viewModel.addSavingsGoal(name, target, current, date, isEmergency)
                        showAddGoalDialog = false
                    }
                )
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 0: OVERVIEW/DASHBOARD
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun OverviewTab(
    viewModel: FinoraaxViewModel,
    user: UserEntity?,
    transactions: List<TransactionEntity>,
    subHealthScore: Int,
    savingsGoals: List<SavingsGoalEntity>,
    onAddTxClick: () -> Unit,
    onAddGoalClick: () -> Unit
) {
    val totalIncome = transactions.filter { it.type == "INCOME" }.sumOf { it.amount }
    val totalExpense = transactions.filter { it.type == "EXPENSE" }.sumOf { it.amount }
    val netBalance = totalIncome - totalExpense

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Welcoming card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.5.dp, GoldPrimary)
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                // Diagonal metallic shimmer gradient backing
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    ObsidianCard,
                                    Color(0xFF1E2226),
                                    ObsidianCard
                                )
                            )
                        )
                )
                
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column {
                            Text("VIRTUAL VAULT SECURITY ACTIVE", fontSize = 9.sp, color = GoldSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Hi, ${user?.name ?: "User"}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = WarmText)
                        }
                        
                        // Cryptographic Vault Dial Ring Vector
                        Canvas(modifier = Modifier.size(40.dp)) {
                            val center = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                            val radius = size.width / 2f
                            
                            drawCircle(
                                color = GoldSecondary.copy(alpha = 0.2f),
                                radius = radius - 2.dp.toPx(),
                                style = Stroke(1.5.dp.toPx())
                            )
                            drawCircle(
                                color = GoldPrimary,
                                radius = radius - 8.dp.toPx(),
                                style = Stroke(2.dp.toPx())
                            )
                            for (angle in 0 until 360 step 45) {
                                val angleRad = Math.toRadians(angle.toDouble())
                                val startX = center.x + (radius - 6.dp.toPx()) * Math.cos(angleRad).toFloat()
                                val startY = center.y + (radius - 6.dp.toPx()) * Math.sin(angleRad).toFloat()
                                val endX = center.x + (radius - 2.dp.toPx()) * Math.cos(angleRad).toFloat()
                                val endY = center.y + (radius - 2.dp.toPx()) * Math.sin(angleRad).toFloat()
                                drawLine(
                                    color = GoldSecondary,
                                    start = androidx.compose.ui.geometry.Offset(startX, startY),
                                    end = androidx.compose.ui.geometry.Offset(endX, endY),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                            drawCircle(
                                color = GoldPrimary,
                                radius = 4.dp.toPx()
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Your financial details are encrypted and analyzed fully on-device.", fontSize = 11.sp, color = MutedText)
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Smartcard contact chip Representation
                        Box(
                            modifier = Modifier
                                .size(38.dp, 28.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(Color(0xFFE5C158), Color(0xFFC5A059), Color(0xFFF3E5AB))
                                    )
                                )
                                .border(1.dp, GoldPrimary, RoundedCornerShape(6.dp))
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val w = size.width
                                val h = size.height
                                drawLine(
                                    color = Color(0x77000000),
                                    start = androidx.compose.ui.geometry.Offset(0f, h / 2f),
                                    end = androidx.compose.ui.geometry.Offset(w, h / 2f),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x77000000),
                                    start = androidx.compose.ui.geometry.Offset(w / 3f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(w / 3f, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                                drawLine(
                                    color = Color(0x77000000),
                                    start = androidx.compose.ui.geometry.Offset(2f * w / 3f, 0f),
                                    end = androidx.compose.ui.geometry.Offset(2f * w / 3f, h),
                                    strokeWidth = 1.dp.toPx()
                                )
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column {
                            Text("TOTAL LIQUID SECURITY BACKING", fontSize = 10.sp, color = GoldSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("₹${"%,.2f".format(netBalance)}", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = GoldPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(GoldPrimary.copy(alpha = 0.15f))
                                .border(1.dp, GoldPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("INR - Default", color = GoldPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Quick Stats row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Inflow Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, JadeGreen.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ObsidianCard, Color(0xFF091F14))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = JadeGreen, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salary Inflow", fontSize = 11.sp, color = MutedText)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₹${"%,.2f".format(totalIncome)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = JadeGreen)
                    }
                }
            }
            
            // Outflow Card
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(ObsidianCard, Color(0xFF1F090C))
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Active Outflow", fontSize = 11.sp, color = MutedText)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("₹${"%,.2f".format(totalExpense)}", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = AlertRed)
                    }
                }
            }
        }

        // App Health and Leak Indicators
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, GoldSecondary.copy(alpha = 0.15f)),
            onClick = {}
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Subscription Health Meter", fontWeight = FontWeight.Bold, color = WarmText, fontSize = 14.sp)
                    Text("Forgotten recurring plans reduce your score.", color = MutedText, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (subHealthScore >= 80) "Optimal Health" else "Leaky Channels Detected", 
                        color = if (subHealthScore >= 80) JadeGreen else AlertRed, 
                        fontWeight = FontWeight.Bold, 
                        fontSize = 13.sp
                    )
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                ) {
                    Canvas(modifier = Modifier.size(54.dp)) {
                        val activeColor = if (subHealthScore >= 80) JadeGreen else GoldPrimary
                        // Background track arc
                        drawArc(
                            color = GoldSecondary.copy(alpha = 0.1f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Glowing arc shadow
                        drawArc(
                            color = activeColor.copy(alpha = 0.25f),
                            startAngle = -90f,
                            sweepAngle = 3.6f * subHealthScore,
                            useCenter = false,
                            style = Stroke(10.dp.toPx(), cap = StrokeCap.Round)
                        )
                        // Solid inner progress arc
                        drawArc(
                            color = activeColor,
                            startAngle = -90f,
                            sweepAngle = 3.6f * subHealthScore,
                            useCenter = false,
                            style = Stroke(6.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text("$subHealthScore%", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = WarmText)
                }
            }
        }

        // Emergency Fund Tracker
        val emergencyGoal = savingsGoals.find { it.isEmergencyFund } ?: SavingsGoalEntity(name = "Emergency Reserve Fund", targetAmount = 15000.0, currentAmount = 8400.0, isEmergencyFund = true)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, GoldSecondary.copy(alpha = 0.15f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Active Protection: Emergency Runway", fontWeight = FontWeight.Bold, color = WarmText, fontSize = 14.sp)
                        Text("Provides cushion for unexpected spikes", color = MutedText, fontSize = 11.sp)
                    }
                    IconButton(onClick = onAddGoalClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Goal", tint = GoldPrimary)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                val target = emergencyGoal.targetAmount
                val current = emergencyGoal.currentAmount
                val progress = if (target > 0) (current / target).toFloat().coerceIn(0f, 1f) else 0f
                
                // Custom Runway Progress Indicator drawing
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF0C0E10))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val width = size.width
                        val height = size.height
                        val progressWidth = width * progress
                        
                        // 1. Draw runway dashes down the center
                        val dashWidth = 8.dp.toPx()
                        val dashGap = 6.dp.toPx()
                        var currentX = 0f
                        while (currentX < width) {
                            drawRect(
                                color = Color(0x22FFFFFF),
                                topLeft = androidx.compose.ui.geometry.Offset(currentX, height / 2f - 1.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(dashWidth, 2.dp.toPx())
                            )
                            currentX += dashWidth + dashGap
                        }
                        
                        // 2. Draw metallic gold fill progress
                        if (progressWidth > 0f) {
                            drawRoundRect(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(GoldSecondary, GoldPrimary)
                                ),
                                size = androidx.compose.ui.geometry.Size(progressWidth, height),
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(7.dp.toPx(), 7.dp.toPx())
                            )
                            
                            // 3. Draw dark asphalt contrast dashes on filled part
                            var filledX = 0f
                            while (filledX < progressWidth) {
                                drawRect(
                                    color = Color(0xFF0C0E10),
                                    topLeft = androidx.compose.ui.geometry.Offset(filledX, height / 2f - 1.dp.toPx()),
                                    size = androidx.compose.ui.geometry.Size(dashWidth, 2.dp.toPx())
                                )
                                filledX += dashWidth + dashGap
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("₹${"%,.0f".format(current)} cushion set", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = WarmText)
                    Text("Goal ₹${"%,.0f".format(target)} (${(progress * 100).toInt()}%)", fontSize = 12.sp, color = MutedText)
                }
            }
        }

        // Quick Actions Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SoftSage.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = onAddTxClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(44.dp).testTag("quick_add_expense_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Record", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = onAddGoalClick,
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp).height(44.dp).testTag("quick_add_goal_btn"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Favorite, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Goal", fontSize = 12.sp)
                    }
                }
            }
        }

        // Recent Entries Ledger
        Text("Recent Ledger Records", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
        
        if (transactions.isEmpty()) {
            Text("No transactions logged. Use ADD to post sample data.", fontSize = 13.sp, color = SageSecondaryText, modifier = Modifier.padding(vertical = 12.dp))
        } else {
            transactions.take(5).forEach { tx ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (tx.type == "INCOME") SageGreen.copy(alpha = 0.15f) else AlertRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (tx.type == "INCOME") Icons.Default.Add else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (tx.type == "INCOME") SageGreen else AlertRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(tx.note.take(24) + if(tx.note.length > 24) "..." else "", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 13.sp)
                                Text("${tx.category} • ${tx.date}", fontSize = 11.sp, color = SageSecondaryText)
                            }
                        }
                        Text(
                            text = (if (tx.type == "INCOME") "+" else "-") + "₹${"%.2f".format(tx.amount)}",
                            fontWeight = FontWeight.Bold,
                            color = if (tx.type == "INCOME") JadeGreen else AlertRed,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 1: TRANSACTIONS AND LEDGER IMPORTER
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun LedgerTab(
    viewModel: FinoraaxViewModel,
    transactions: List<TransactionEntity>
) {
    val context = LocalContext.current
    var importResultText by remember { mutableStateOf("") }
    var showImportBox by remember { mutableStateOf(false) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importStatementFile(context, it) { msg ->
                importResultText = msg
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bank Statement Importer", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = SagePrimaryText)
                        IconButton(onClick = { showImportBox = !showImportBox }) {
                            Icon(imageVector = if (showImportBox) Icons.Default.Close else Icons.Default.Add, contentDescription = null, tint = SageGreen)
                        }
                    }
                    Text("Select a bank statement file (CSV or PDF) to import transactions securely.", color = SageSecondaryText, fontSize = 11.sp)
                    
                    if (showImportBox) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                filePickerLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "application/pdf"))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("select_statement_file_btn")
                        ) {
                            Text("Select Statement File", color = SageWhiteCard, fontWeight = FontWeight.Bold)
                        }

                        if (importResultText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = importResultText, 
                                color = if (importResultText.contains("Successfully")) SageGreen else AlertRed, 
                                fontWeight = FontWeight.SemiBold, 
                                fontSize = 11.sp, 
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }

        item {
            Text("Transaction Log History", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 16.sp, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp).fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = SoftSage, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Ledger is completely empty", fontWeight = FontWeight.Bold, color = SagePrimaryText)
                        Text("Import statement rows above or record manually below.", color = SageSecondaryText, textAlign = TextAlign.Center, fontSize = 12.sp)
                    }
                }
            }
        } else {
            items(transactions) { tx ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (tx.type == "INCOME") SoftSage else AlertRed.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (tx.type == "INCOME") Icons.Default.Add else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if (tx.type == "INCOME") SageGreen else AlertRed,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(tx.note, fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 13.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("${tx.category} • ${tx.date}", fontSize = 11.sp, color = SageSecondaryText)
                                    if (tx.isRecurring) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(Icons.Default.Refresh, contentDescription = "Autopay", tint = SageGreen, modifier = Modifier.size(10.dp))
                                    }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = (if (tx.type == "INCOME") "+" else "-") + "₹${"%.2f".format(tx.amount)}",
                                fontWeight = FontWeight.Bold,
                                color = if (tx.type == "INCOME") SageGreen else AlertRed,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { viewModel.deleteTransaction(tx.id) }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed.copy(alpha = 0.7f), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 2: BUDGETS AND GOALS
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun BudgetsAndGoalsTab(
    viewModel: FinoraaxViewModel,
    budgets: List<BudgetEntity>,
    goals: List<SavingsGoalEntity>,
    onAddGoal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Budgeting section
        Text("June Budget Threshold Caps", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))
        
        if (budgets.isEmpty()) {
            Text("No active budget configurations. Add transaction items to test triggers.", color = SageSecondaryText, fontSize = 12.sp)
        } else {
            budgets.forEach { b ->
                val progress = if (b.limitAmount > 0) (b.spentAmount / b.limitAmount).toFloat().coerceIn(0f, 1.2f) else 0f
                val barProgress = progress.coerceAtMost(1f)
                val overspent = b.spentAmount > b.limitAmount

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(b.category, fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 14.sp)
                            Text(
                                if(overspent) "Overspent!" else "₹${"%,.0f".format(b.spentAmount)} of ₹${"%,.0f".format(b.limitAmount)}",
                                color = if (overspent) AlertRed else SageGreen,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { barProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = if (overspent) AlertRed else SageGreen,
                            trackColor = SoftSage.copy(alpha = 0.2f)
                        )
                        if (overspent) {
                            Text(
                                text = "Exceeds threshold cap by ₹${"%,.2f".format(b.spentAmount - b.limitAmount)} - Leakage warnings sent",
                                color = AlertRed,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 4.dp),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Saving Goals section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Savings Goals Portfolio", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 16.sp)
            IconButton(onClick = onAddGoal) {
                Icon(Icons.Default.AddCircle, contentDescription = "New Goal", tint = SageGreen)
            }
        }

        if (goals.isEmpty()) {
            Text("No goals entered.", color = SageSecondaryText, fontSize = 12.sp)
        } else {
            goals.forEach { g ->
                val progress = if (g.targetAmount > 0) (g.currentAmount / g.targetAmount).toFloat().coerceIn(0f, 1f) else 0f
                var targetProgressVal by remember { mutableStateOf(g.currentAmount.toString()) }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (g.isEmergencyFund) Icons.Default.Lock else Icons.Default.Favorite,
                                    contentDescription = null,
                                    tint = if (g.isEmergencyFund) SageGreen else YellowAccent
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(g.name, fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 14.sp)
                            }
                            IconButton(onClick = { viewModel.deleteSavingsGoal(g.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = AlertRed.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                            }
                        }
                        
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = SageGreen,
                            trackColor = SoftSage.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "₹${"%,.0f".format(g.currentAmount)} of ₹${"%,.0f".format(g.targetAmount)} (${(progress * 100).toInt()}%)",
                                color = SageSecondaryText,
                                fontSize = 12.sp
                            )
                            Text("Due ${g.targetDate}", fontSize = 11.sp, color = SageSecondaryText)
                        }

                        // Save update inline form
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = targetProgressVal,
                                onValueChange = { targetProgressVal = it },
                                leadingIcon = { Text("₹", color = SageSecondaryText) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val amt = targetProgressVal.toDoubleOrNull() ?: g.currentAmount
                                    viewModel.updateSavingsGoal(g.id, amt)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = SageWhiteCard),
                                border = BorderStroke(1.dp, SoftSage),
                                modifier = Modifier.height(44.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Update Progress", color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 3: VISUAL CASH FLOW CALENDAR
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun CashFlowCalendarTab(
    viewModel: FinoraaxViewModel,
    transactions: List<TransactionEntity>,
    bills: List<BillEntity>
) {
    var selectedDay by remember { mutableStateOf(14) } // Default Day selected: June 14
    
    // Setup transactions associated to each day in June 2026
    val daysInMonth = 30
    
    // Quick helper to determine color of date cell
    fun getDayVisualType(day: Int): String {
        // Pad date string e.g. "2026-06-02"
        val dateStr = "2026-06-${day.toString().padStart(2, '0')}"
        val daysTx = transactions.filter { it.date == dateStr }
        val daysBills = bills.filter { it.dueDate == dateStr }
        
        return when {
            daysTx.any { it.type == "INCOME" } -> "INCOME" // Green day
            daysBills.isNotEmpty() -> "BILL" // Red day
            daysTx.any { it.type == "EXPENSE" && it.amount >= 100 } -> "HIGH_EXPENSE" // Yellow Day
            else -> "DEFAULT"
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text("Encrypted Cash Flow Calendar", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 16.sp)
        Text("Smart Heatmap: Green Inflow / Red Bills due / Yellow Spikes (>8,000 INR)", color = SageSecondaryText, fontSize = 11.sp, modifier = Modifier.padding(bottom = 12.dp))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Month Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("June 2026", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = SageGreen)
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(SoftSage, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Inflow", fontSize = 10.sp, color = SageSecondaryText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(AlertRed.copy(alpha = 0.3f), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Bill due", fontSize = 10.sp, color = SageSecondaryText)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).background(YellowAccent.copy(alpha = 0.5f), CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Spike", fontSize = 10.sp, color = SageSecondaryText)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Days of week banner
                val dayHeaders = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    dayHeaders.forEach {
                        Text(it, fontWeight = FontWeight.Bold, color = SageSecondaryText, fontSize = 12.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Render Calendar blocks (June 2026 starts on Monday June 1)
                val totalWeeks = 5 // 30 days fits nicely
                var currentDayCount = 1

                for (w in 0 until totalWeeks) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        for (d in 0 until 7) {
                            val activeDay = currentDayCount
                            if (activeDay <= daysInMonth) {
                                val type = getDayVisualType(activeDay)
                                val cellBgColor = when (type) {
                                    "INCOME" -> SoftSage.copy(alpha = 0.25f)
                                    "BILL" -> AlertRed.copy(alpha = 0.25f)
                                    "HIGH_EXPENSE" -> YellowAccent.copy(alpha = 0.4f)
                                    else -> Color.Transparent
                                }
                                val isSelected = selectedDay == activeDay

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .padding(3.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(cellBgColor)
                                        .border(
                                            width = if (isSelected) 2.dp else 0.dp,
                                            color = if (isSelected) SageGreen else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedDay = activeDay }
                                        .testTag("calendar_day_$activeDay"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = activeDay.toString(),
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                        fontSize = 12.sp,
                                        color = SagePrimaryText
                                    )
                                }
                                currentDayCount++
                            } else {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Selected Day Forecast / Events Panel
        val targetDateStr = "2026-06-${selectedDay.toString().padStart(2, '0')}"
        val dayTransactions = transactions.filter { it.date == targetDateStr }
        val dayBills = bills.filter { it.dueDate == targetDateStr }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Events & Projections for Jun $selectedDay, 2026",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = SagePrimaryText
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (dayTransactions.isEmpty() && dayBills.isEmpty()) {
                    Text("No visual records scheduled for this day. Secure cash flow stable.", fontSize = 12.sp, color = SageSecondaryText)
                } else {
                    dayTransactions.forEach { tx ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if(tx.type == "INCOME") Icons.Default.Add else Icons.Default.Close,
                                    contentDescription = null,
                                    tint = if(tx.type == "INCOME") SageGreen else AlertRed,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(tx.note, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = SagePrimaryText)
                            }
                            Text(
                                text = (if(tx.type == "INCOME") "+" else "-") + "₹${"%.2f".format(tx.amount)}",
                                color = if(tx.type == "INCOME") SageGreen else AlertRed,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    dayBills.forEach { b ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.DateRange, contentDescription = null, tint = AlertRed, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upcoming Bill: ${b.name}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AlertRed)
                            }
                            Button(
                                onClick = { viewModel.payBill(b.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = if(b.isPaid) SoftSage else AlertRed),
                                modifier = Modifier.height(28.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                shape = RoundedCornerShape(6.dp),
                                enabled = !b.isPaid
                            ) {
                                Text(if(b.isPaid) "Paid" else "Pay ₹${b.amount}", color = if(b.isPaid) SageGreen else SageWhiteCard, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 4: SUBSCRIPTION LEAK DETECTOR Screen
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun SubscriptionLeakDetectorTab(
    viewModel: FinoraaxViewModel,
    subscriptions: List<SubscriptionEntity>,
    subHealthScore: Int
) {
    val activeLeaks = subscriptions.filter { it.status == "Active" && it.isForgotten }
    val regularActive = subscriptions.filter { it.status == "Active" && !it.isForgotten }
    val cancelledPlans = subscriptions.filter { it.status == "Cancelled" }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // Health score panel
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Subscription Health Meter", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = SagePrimaryText)
                Text("Proactively flags trials and gym passes with 0% checked usage.", color = SageSecondaryText, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(100.dp)
                ) {
                    Canvas(modifier = Modifier.size(90.dp)) {
                        drawArc(
                            color = SoftSage,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                        )
                        drawArc(
                            color = if (subHealthScore >= 80) SageGreen else AlertRed,
                            startAngle = -90f,
                            sweepAngle = 3.6f * subHealthScore,
                            useCenter = false,
                            style = Stroke(8.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    Text("$subHealthScore", fontSize = 28.sp, fontWeight = FontWeight.Black, color = SagePrimaryText)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if(activeLeaks.isEmpty()) "Optimal Cash Flow Secured! Zero active leaks." else "Direct Leak Action Required!",
                    color = if(activeLeaks.isEmpty()) SageGreen else AlertRed,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        // Segment 1: Leaking forgotten subscription items
        Text("Leaking Subscriptions Found (${activeLeaks.size})", fontWeight = FontWeight.Bold, color = AlertRed, fontSize = 15.sp, modifier = Modifier.padding(vertical = 8.dp))
        
        if (activeLeaks.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = SoftSage.copy(alpha = 0.2f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = SageGreen)
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Superb job. Your recurrent autopays are fully utilized.", color = SagePrimaryText, fontSize = 12.sp)
                }
            }
        } else {
            activeLeaks.forEach { leak ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, AlertRed.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(leak.name, fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 14.sp)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = YellowAccent, modifier = Modifier.size(10.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(leak.leakReason, fontSize = 11.sp, color = AlertRed, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text(
                                text = "₹${leak.cost}/${if(leak.billingCycle == "Monthly") "mo" else "yr"}",
                                fontWeight = FontWeight.ExtraBold,
                                color = AlertRed,
                                fontSize = 14.sp
                            )
                        }
                        
                        Text(
                            text = "Advisor Tip: " + leak.optimizationSuggestion, 
                            color = SageSecondaryText, 
                            fontSize = 11.sp, 
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { viewModel.cancelSubscription(leak.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = AlertRed),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Cancel Autopay", color = SageWhiteCard, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { viewModel.keepSubscription(leak.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = SageWhiteCard),
                                border = BorderStroke(1.dp, SoftSage),
                                modifier = Modifier.weight(1f).height(38.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Acknowledge Plan", color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Segment 2: Normal active active recurring plans
        Text("Active Managed Recurring Channels (${regularActive.size})", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        
        regularActive.forEach { sub ->
            Card(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(sub.name, fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 13.sp)
                        Text("Next renewal: ${sub.nextRenewalDate}", fontSize = 11.sp, color = SageSecondaryText)
                    }
                    Text(
                        text = "₹${sub.cost}/${if(sub.billingCycle == "Monthly") "mo" else "yr"}",
                        fontWeight = FontWeight.Bold,
                        color = SageGreen,
                        fontSize = 13.sp
                    )
                }
            }
        }

        // Segment 3: Cancelled / Clean plans
        if (cancelledPlans.isNotEmpty()) {
            Text("Decommissioned leaks (${cancelledPlans.size})", fontWeight = FontWeight.Bold, color = SageSecondaryText, fontSize = 14.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
            cancelledPlans.forEach { sub ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = CardDefaults.cardColors(containerColor = SageWhiteCard.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(sub.name, fontWeight = FontWeight.Bold, color = SageSecondaryText, fontSize = 13.sp)
                            Text("Autodebit cancelled fully.", fontSize = 11.sp, color = SageGreen, fontWeight = FontWeight.Bold)
                        }
                        Text(
                            text = "Saved ₹${sub.cost}",
                            color = SageGreen,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 5: SMART AI ADVISOR & CHATBOT
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun TypingIndicator(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "typing")
    val dot1Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.4f at 0
                1.0f at 200
                0.4f at 400
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot1"
    )
    val dot2Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.4f at 150
                1.0f at 350
                0.4f at 550
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot2"
    )
    val dot3Scale by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 600
                0.4f at 300
                1.0f at 500
                0.4f at 600
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "dot3"
    )

    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        listOf(dot1Scale, dot2Scale, dot3Scale).forEach { scale ->
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .clip(CircleShape)
                    .background(GoldPrimary)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SmartFinancialAdvisorTab(
    viewModel: FinoraaxViewModel,
    chatHistory: List<Pair<String, Boolean>>,
    isAnalyzing: Boolean,
    advisorAdvice: String
) {
    var chatInputText by remember { mutableStateOf("") }
    val isApiKeyValid = remember { GeminiClient.isApiKeyValid() }
    var isAuditExpanded by remember { mutableStateOf(false) }
    var isInputFocused by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current

    // Auto-scroll to the bottom when chatHistory changes or input field gets focus
    LaunchedEffect(chatHistory.size, isInputFocused) {
        if (chatHistory.isNotEmpty()) {
            listState.animateScrollToItem(chatHistory.size - 1)
        }
    }

    // Auto-dismiss focus and keyboard when back key is pressed (IME visibility drops)
    val isKeyboardVisible = WindowInsets.isImeVisible
    LaunchedEffect(isKeyboardVisible) {
        if (!isKeyboardVisible) {
            focusManager.clearFocus()
        }
    }

    // Collect Room DB data to drive dynamic shortcuts
    val transactions by viewModel.transactionsState.collectAsState()
    val budgets by viewModel.budgetsState.collectAsState()
    val savingsGoals by viewModel.savingsGoalsState.collectAsState()
    val subscriptions by viewModel.subscriptionsState.collectAsState()
    val chatSuggestions by viewModel.chatSuggestions.collectAsState()

    // Formulate dynamic context-aware shortcuts
    val dynamicShortcuts = remember(transactions, budgets, savingsGoals, subscriptions) {
        val list = mutableListOf<String>()
        val hasLeakySubs = subscriptions.any { it.isForgotten && it.status == "Active" }
        val hasOverspent = budgets.any { it.spentAmount > it.limitAmount }
        val hasEmergencyGoal = savingsGoals.any { it.isEmergencyFund }

        if (hasLeakySubs) {
            list.add("Optimize Leaks")
        } else {
            list.add("Subscription Audit")
        }

        if (hasOverspent) {
            list.add("Fix Overspending")
        } else {
            list.add("Budget Caps")
        }

        if (hasEmergencyGoal) {
            list.add("Runway Tips")
        } else {
            list.add("Savings Advice")
        }
        list
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                focusManager.clearFocus()
            }
    ) {
        // Executive Audit advice section (collapsible manually via chevron)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp)
                .clickable { isAuditExpanded = !isAuditExpanded },
            colors = CardDefaults.cardColors(containerColor = ObsidianCard),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, GoldPrimary.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("EXECUTIVE LOCAL SECURITY AUDIT", fontSize = 8.sp, color = GoldSecondary, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Autonomous Advisor Recommendation",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = WarmText
                        )
                    }
                    Icon(
                        imageVector = if (isAuditExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = "Toggle Audit",
                        tint = GoldPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                
                AnimatedVisibility(visible = isAuditExpanded) {
                    Column {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = advisorAdvice,
                            color = MutedText,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = { viewModel.generateExpertAdvice() },
                            colors = ButtonDefaults.buttonColors(containerColor = ObsidianBackground),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth().height(38.dp).testTag("trigger_audit_btn"),
                            enabled = !isAnalyzing
                        ) {
                            Text(
                                text = if (isAnalyzing) "Processing local telemetry..." else "Generate Advisor Recommendation",
                                color = GoldPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Financial chatbot chat container header
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Finoraax AI Chatbot Coach", fontWeight = FontWeight.ExtraBold, color = SagePrimaryText, fontSize = 15.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Your chat coordinates standard API structures through local contexts.", color = SageSecondaryText, fontSize = 10.sp)
            }
            IconButton(
                onClick = { viewModel.clearChatHistory() },
                modifier = Modifier.size(36.dp).testTag("clear_chat_history_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Clear History",
                    tint = AlertRed.copy(alpha = 0.8f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Large Box to hold Chat messages
        Card(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                // Connection/Sandbox status banner
                Box(modifier = Modifier.padding(bottom = 8.dp)) {
                    if (isApiKeyValid) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(JadeGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, JadeGreen.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Active Connection",
                                tint = JadeGreen,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Secure Local Gemini Connection Active",
                                color = JadeGreen,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(GoldSecondary.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                .border(1.dp, GoldSecondary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "Sandbox Warning",
                                tint = GoldSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Sandbox Mode Active (Using local mock engine)",
                                color = GoldSecondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Messages scrolling list
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(chatHistory) { (msgText, isModel) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = if (isModel) Arrangement.Start else Arrangement.End,
                            verticalAlignment = Alignment.Top
                        ) {
                            if (isModel) {
                                // AI Robot Avatar
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.12f))
                                        .border(1.dp, GoldPrimary.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = "Advisor AI",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                            }

                            // Speech bubble container
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 240.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomStart = if (isModel) 0.dp else 16.dp,
                                            bottomEnd = if (isModel) 16.dp else 0.dp
                                        )
                                    )
                                    .let {
                                        if (isModel) {
                                            it.background(ObsidianBackground)
                                                .border(1.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp))
                                        } else {
                                            it.background(
                                                Brush.linearGradient(
                                                    colors = listOf(GoldPrimary, GoldSecondary)
                                                )
                                            )
                                        }
                                    }
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = msgText,
                                    fontSize = 12.sp,
                                    color = if (isModel) WarmText else ObsidianBackground,
                                    lineHeight = 18.sp
                                )
                            }

                            if (!isModel) {
                                Spacer(modifier = Modifier.width(8.dp))
                                // User Avatar
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldSecondary.copy(alpha = 0.12f))
                                        .border(1.dp, GoldSecondary.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "User",
                                        tint = GoldSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                    
                    if (isAnalyzing) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.Start,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // AI Robot Avatar for typing state
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .background(GoldPrimary.copy(alpha = 0.12f))
                                        .border(1.dp, GoldPrimary.copy(alpha = 0.25f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Face,
                                        contentDescription = "Advisor AI",
                                        tint = GoldPrimary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp))
                                        .background(ObsidianBackground)
                                        .border(1.dp, GoldPrimary.copy(alpha = 0.2f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 0.dp, bottomEnd = 16.dp))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    TypingIndicator()
                                }
                            }
                        }
                    }
                }

                // Dynamic shortcuts chips
                val displaySuggestions = if (chatSuggestions.isNotEmpty()) chatSuggestions else dynamicShortcuts
                Column {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    // Fast prompt shortcuts chips
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        displaySuggestions.forEach { p ->
                            Box(
                                modifier = Modifier
                                    .border(1.dp, SoftSage, RoundedCornerShape(12.dp))
                                    .clickable {
                                        viewModel.sendMessageToAdvisor(p)
                                        focusManager.clearFocus()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                    .testTag("shortcut_$p")
                            ) {
                                Text(p, fontSize = 10.sp, color = SageGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                // Chat Input box row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = chatInputText,
                        onValueChange = { chatInputText = it },
                        placeholder = { Text("Ask about budgets, goals, leaks...", fontSize = 11.sp) },
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { isInputFocused = it.isFocused }
                            .testTag("chat_input"),
                        shape = RoundedCornerShape(12.dp),
                        textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SageGreen,
                            unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                            unfocusedContainerColor = SageBackground,
                            focusedContainerColor = SageWhiteCard
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (chatInputText.isNotBlank()) {
                                viewModel.sendMessageToAdvisor(chatInputText)
                                chatInputText = ""
                                focusManager.clearFocus()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(SageGreen, CircleShape)
                            .testTag("send_chat_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = SageWhiteCard, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// TAB 6: INVESTMENTS, CURRENCY CONVERTER, SECURITY & OTHER UTILITIES
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun InvestmentsAndSettingsTab(
    viewModel: FinoraaxViewModel,
    investments: List<InvestmentEntity>,
    user: UserEntity?,
    onLogout: () -> Unit
) {
    var rawCurrencyVal by remember { mutableStateOf("100") }
    var selectedToCurrency by remember { mutableStateOf("EUR") }
    var convertedOutput by remember { mutableStateOf("") }
    var liveRates by remember { mutableStateOf<Map<String, Double>?>(null) }
    var isLoadingRates by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoadingRates = true
        val rates = com.example.service.CurrencyClient.fetchRates()
        if (rates != null) {
            liveRates = rates
        }
        isLoadingRates = false
    }

    val totalInvestmentVal = investments.sumOf { it.currentAmount }
    val totalInvestmentsInit = investments.sumOf { it.initialAmount }
    val growthRate = if (totalInvestmentsInit > 0) ((totalInvestmentVal - totalInvestmentsInit) / totalInvestmentsInit) * 100 else 0.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .imePadding()
    ) {
        // Investment Profile Portfolio Panel
        Text("Active Growth Investment Portfolio", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("PORTFOLIO NET METRIC VALUE", fontSize = 10.sp, color = SageSecondaryText, fontWeight = FontWeight.Bold)
                        Text("₹${"%,.2f".format(totalInvestmentVal)}", fontSize = 24.sp, fontWeight = FontWeight.Black, color = SagePrimaryText)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SageGreen.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("+${"%.1f".format(growthRate)}% Growth", color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Black)
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))

                // List individual investment assets
                investments.forEach { inv ->
                    val assetGrowth = ((inv.currentAmount - inv.initialAmount) / inv.initialAmount) * 100
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(inv.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SagePrimaryText)
                            Text("${inv.type} • ${inv.units} units", fontSize = 10.sp, color = SageSecondaryText)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("₹${"%,.2f".format(inv.currentAmount)}", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = SagePrimaryText)
                            Text("+${"%.1f".format(assetGrowth)}%", fontSize = 10.sp, color = SageGreen, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }

        // Live Local Currency Converter Widget
        Text("Interactive Currency Converter", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = rawCurrencyVal,
                        onValueChange = { rawCurrencyVal = it },
                        label = { Text("USD Base Amount", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1.3f).testTag("currency_usd_input"),
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = SagePrimaryText,
                            unfocusedTextColor = SagePrimaryText,
                            focusedBorderColor = SageGreen,
                            unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                            focusedContainerColor = SageWhiteCard,
                            unfocusedContainerColor = SageBackground,
                            focusedLabelColor = SageGreen,
                            unfocusedLabelColor = SageSecondaryText
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Destination selector
                    val currencies = listOf("EUR", "INR", "GBP", "CAD")
                    var dropdownExpanded by remember { mutableStateOf(false) }
                    Box(modifier = Modifier.weight(0.8f)) {
                        Button(
                            onClick = { dropdownExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = SageWhiteCard),
                            border = BorderStroke(1.dp, SoftSage),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().height(44.dp)
                        ) {
                            Text(selectedToCurrency, color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier.background(SageWhiteCard)
                        ) {
                            currencies.forEach { curr ->
                                DropdownMenuItem(
                                    text = { Text(curr, color = SagePrimaryText, fontSize = 12.sp) },
                                    onClick = {
                                        selectedToCurrency = curr
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        val usd = rawCurrencyVal.toDoubleOrNull() ?: 100.0
                        val rateMap = liveRates
                        val rate = if (rateMap != null && rateMap.containsKey(selectedToCurrency)) {
                            rateMap[selectedToCurrency] ?: 1.0
                        } else {
                            when(selectedToCurrency) {
                                "EUR" -> 0.92
                                "INR" -> 83.45
                                "GBP" -> 0.79
                                "CAD" -> 1.37
                                else -> 1.0
                            }
                        }
                        val source = if (rateMap != null && rateMap.containsKey(selectedToCurrency)) "live online rates" else "offline rates"
                        convertedOutput = "$${"%,.2f".format(usd)} USD = ${"%,.2f".format(usd * rate)} $selectedToCurrency\n*(Using $source: $rate)"
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("convert_currency_btn")
                ) {
                    if (isLoadingRates) {
                        CircularProgressIndicator(color = SageWhiteCard, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Convert Live Rates", color = SageWhiteCard, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                if (convertedOutput.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(convertedOutput, color = SageGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                }
            }
        }

        // Active Local Encryption Sessions Audit
        Text("Active Sessions & Encryption Profiles", fontWeight = FontWeight.Bold, color = SagePrimaryText, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Lock, contentDescription = null, tint = SageGreen)
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text("Session Vault Key: Active", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SagePrimaryText)
                        Text("Token: " + (user?.sessionToken?.take(16) ?: "Unassigned") + "...", fontSize = 10.sp, color = SageSecondaryText)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text("Registered Profile Name: ${user?.name ?: "Finoraax Member"}", fontSize = 11.sp, color = SagePrimaryText)
                Text("Registered Profile Email: ${user?.email ?: "member@finoraax.local"}", fontSize = 11.sp, color = SagePrimaryText)
                Text("Key Vault Storage Algorithm: SQLite AES-256 local keys", fontSize = 10.sp, color = SageSecondaryText)
            }
        }

        // Reseed / Help Utilities
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = SageWhiteCard),
            border = BorderStroke(1.dp, SoftSage.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sandbox Controls", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = SagePrimaryText)
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.reseedDatabase() },
                    colors = ButtonDefaults.buttonColors(containerColor = SageWhiteCard),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, SoftSage)
                ) {
                    Text("Reseed Mock Ledger & Sandbox Data", color = SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MODAL DIALOGS: MANUAL TRANSACTION ADDER
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionDialog(
    onDismiss: () -> Unit,
    onSave: (type: String, category: String, amount: Double, date: String, note: String) -> Unit
) {
    var type by remember { mutableStateOf("EXPENSE") } // INCOME or EXPENSE
    var category by remember { mutableStateOf("Groceries") }
    var amount by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("2026-06-14") }
    var errorMessage by remember { mutableStateOf("") }

    val categoriesList = listOf("Groceries", "Salary", "Entertainment", "Utilities", "Dining", "Investment Returns")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log New Record Securely", fontWeight = FontWeight.Bold, color = SagePrimaryText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Selector Row
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { type = "INCOME"; if(category=="Groceries") category="Salary" },
                        colors = ButtonDefaults.buttonColors(containerColor = if(type == "INCOME") SageGreen else SageWhiteCard),
                        border = if (type == "INCOME") null else BorderStroke(1.dp, SoftSage),
                        modifier = Modifier.weight(1f).testTag("select_income")
                    ) {
                        Text("income", color = if(type == "INCOME") SageWhiteCard else SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = { type = "EXPENSE"; if(category=="Salary") category="Groceries" },
                        colors = ButtonDefaults.buttonColors(containerColor = if(type == "EXPENSE") SageGreen else SageWhiteCard),
                        border = if (type == "EXPENSE") null else BorderStroke(1.dp, SoftSage),
                        modifier = Modifier.weight(1f).testTag("select_expense")
                    ) {
                        Text("expense", color = if(type == "EXPENSE") SageWhiteCard else SageGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Category Selection Dropdown
                var expandedDropdown by remember { mutableStateOf(false) }
                Column {
                    Text("Category", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = SageSecondaryText)
                    Button(
                        onClick = { expandedDropdown = true },
                        colors = ButtonDefaults.buttonColors(containerColor = SageWhiteCard),
                        border = BorderStroke(1.dp, SoftSage),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp)
                    ) {
                        Text(category, color = SageGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    DropdownMenu(
                        expanded = expandedDropdown,
                        onDismissRequest = { expandedDropdown = false },
                        modifier = Modifier.background(SageWhiteCard)
                    ) {
                        categoriesList.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat, color = SagePrimaryText) },
                                onClick = {
                                    category = cat
                                    expandedDropdown = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount (₹ INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_tx_amount"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date (YYYY-MM-DD)") },
                    placeholder = { Text("2026-06-14") },
                    modifier = Modifier.fillMaxWidth().testTag("add_tx_date"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Short Description / Note") },
                    modifier = Modifier.fillMaxWidth().testTag("add_tx_note"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleAmt = amount.toDoubleOrNull()
                    if (doubleAmt == null || doubleAmt <= 0) {
                        errorMessage = "Please input a positive numeric value for amount."
                    } else if (note.isBlank()) {
                        errorMessage = "Short description cannot be blank."
                    } else if (date.length != 10) {
                        errorMessage = "Please use YYYY-MM-DD date format."
                    } else {
                        onSave(type, category, doubleAmt, date, note)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                modifier = Modifier.testTag("submit_add_tx_btn")
            ) {
                Text("Log Transaction", color = SageWhiteCard)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = SageWhiteCard
    )
}

// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
// MODAL DIALOGS: SAVINGS GOAL ADDER
// ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
@Composable
fun AddSavingsGoalDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, target: Double, current: Double, date: String, isEmergencyFund: Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var targetAmount by remember { mutableStateOf("") }
    var currentAmount by remember { mutableStateOf("0") }
    var targetDate by remember { mutableStateOf("2026-12-31") }
    var isEmergency by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log New Wealth Goal", fontWeight = FontWeight.Bold, color = SagePrimaryText) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Goal Name (e.g. Europe Trip)") },
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_name"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                OutlinedTextField(
                    value = targetAmount,
                    onValueChange = { targetAmount = it },
                    label = { Text("Target Goal (₹ INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_target_amt"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                OutlinedTextField(
                    value = currentAmount,
                    onValueChange = { currentAmount = it },
                    label = { Text("Starting Fund Cushion (₹ INR)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_current_amt"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                OutlinedTextField(
                    value = targetDate,
                    onValueChange = { targetDate = it },
                    label = { Text("Due Date (YYYY-MM-DD)") },
                    placeholder = { Text("2026-12-31") },
                    modifier = Modifier.fillMaxWidth().testTag("add_goal_due_date"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SageGreen,
                        unfocusedBorderColor = SageSecondaryText.copy(alpha = 0.5f),
                        unfocusedContainerColor = SageBackground,
                        focusedContainerColor = SageWhiteCard
                    )
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = isEmergency,
                        onCheckedChange = { isEmergency = it }
                    )
                    Text("Is Emergency Backup Fund", fontSize = 12.sp, color = SagePrimaryText, fontWeight = FontWeight.SemiBold)
                }

                if (errorMessage.isNotEmpty()) {
                    Text(errorMessage, color = AlertRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleTrg = targetAmount.toDoubleOrNull()
                    val doubleCur = currentAmount.toDoubleOrNull() ?: 0.0
                    if (name.isBlank()) {
                        errorMessage = "Goal name cannot be blank."
                    } else if (doubleTrg == null || doubleTrg <= 0) {
                        errorMessage = "Please enter positive target goal amount."
                    } else if (doubleCur < 0 || doubleCur > doubleTrg) {
                        errorMessage = "Starting fund must be between 0 and your target."
                    } else if (targetDate.length != 10) {
                        errorMessage = "Please use YYYY-MM-DD date format."
                    } else {
                        onSave(name, doubleTrg, doubleCur, targetDate, isEmergency)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = SageGreen),
                modifier = Modifier.testTag("submit_add_goal_btn")
            ) {
                Text("Establish Goal", color = SageWhiteCard)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        containerColor = SageWhiteCard
    )
}

// Additional color placeholders
val SunkenCardBgColor = ObsidianBackground
val ShieldColor = GoldPrimary
val IconsColor = GoldPrimary
val IconsSecondaryColor = MutedText
