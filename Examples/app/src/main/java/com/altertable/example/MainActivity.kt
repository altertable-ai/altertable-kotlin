package com.altertable.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.altertable.sdk.Altertable
import ai.altertable.sdk.android.ProvideAltertable
import ai.altertable.sdk.android.TrackScreenView

private val Blue = Color(0xFF2196F3)
private val BlueLight = Color(0x1A2196F3)
private val Green = Color(0xFF4CAF50)
private val ErrorRed = Color(0xFFE53935)
private val SurfaceGray = Color(0xFFF5F5F5)
private val BorderGray = Color(0x33000000)
private val SecondaryText = Color(0xFF757575)

private val CornerRadiusSmall = 8.dp
private val CornerRadiusCard = 12.dp
private val StepIconSize = 40.dp
private val CheckBadgeSize = 18.dp
private val ProgressLineHeight = 2.dp
private val WelcomeIconSize = 80.dp

enum class Plan(val title: String, val price: Int, val description: String) {
    STARTER("Starter", 9, "Perfect for individuals"),
    PRO("Pro", 29, "Best for small teams"),
    ENTERPRISE("Enterprise", 99, "For large organizations"),
}

data class SignupStep(val id: Int, val title: String, val icon: ImageVector)

private val allSteps = listOf(
    SignupStep(1, "Personal Info", Icons.Default.AccountBox),
    SignupStep(2, "Account Setup", Icons.Default.Email),
    SignupStep(3, "Choose Plan", Icons.Default.CreditCard),
    SignupStep(4, "Welcome!", Icons.Default.Star),
)

private object FormDefaults {
    const val FIRST_NAME = "John"
    const val LAST_NAME = "Doe"
    const val EMAIL = "john.doe@example.com"
    const val PASSWORD = "password"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SignupFunnelApp()
        }
    }
}

@Composable
fun SignupFunnelApp() {
    MaterialTheme {
        Altertable.shared?.let { client ->
            ProvideAltertable(client) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    SignupFunnelView()
                }
            }
        } ?: run {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                SignupFunnelView()
            }
        }
    }
}

@Composable
fun SignupFunnelView() {
    var currentStep by remember { mutableIntStateOf(1) }
    var firstName by remember { mutableStateOf(FormDefaults.FIRST_NAME) }
    var lastName by remember { mutableStateOf(FormDefaults.LAST_NAME) }
    var email by remember { mutableStateOf(FormDefaults.EMAIL) }
    var password by remember { mutableStateOf(FormDefaults.PASSWORD) }
    var confirmPassword by remember { mutableStateOf(FormDefaults.PASSWORD) }
    var selectedPlan by remember { mutableStateOf(Plan.STARTER) }
    var agreeToTerms by remember { mutableStateOf(false) }
    var errors by remember { mutableStateOf(mapOf<String, String>()) }

    fun validateStep(): Boolean {
        val newErrors = mutableMapOf<String, String>()
        when (currentStep) {
            1 -> {
                if (firstName.isBlank()) newErrors["firstName"] = "First name is required"
                if (lastName.isBlank()) newErrors["lastName"] = "Last name is required"
            }
            2 -> {
                if (email.isBlank()) newErrors["email"] = "Email is required"
                else if (!email.contains("@") || !email.contains(".")) newErrors["email"] = "Enter a valid email address"
                if (password.isBlank()) newErrors["password"] = "Password is required"
                if (password != confirmPassword) newErrors["confirmPassword"] = "Passwords do not match"
            }
            3 -> {
                if (!agreeToTerms) newErrors["agreeToTerms"] = "You must agree to the terms"
            }
        }
        errors = newErrors
        return newErrors.isEmpty()
    }

    fun handleSubmit() {
        Altertable.shared?.track(event = "Form Submitted")
        Altertable.shared?.identify(userId = email)
        currentStep = 4
    }

    fun nextStep() {
        if (!validateStep()) return
        when (currentStep) {
            1 -> Altertable.shared?.track(event = "Personal Info Completed", properties = mapOf("step" to 1))
            2 -> Altertable.shared?.track(event = "Account Setup Completed", properties = mapOf("step" to 2))
            3 -> {
                Altertable.shared?.track(event = "Plan Selection Completed", properties = mapOf("step" to 3))
                handleSubmit()
                return
            }
        }
        currentStep++
    }

    fun prevStep() {
        currentStep--
    }

    fun handleRestart() {
        Altertable.shared?.track(event = "Form Restarted")
        currentStep = 1
        firstName = FormDefaults.FIRST_NAME
        lastName = FormDefaults.LAST_NAME
        email = FormDefaults.EMAIL
        password = FormDefaults.PASSWORD
        confirmPassword = FormDefaults.PASSWORD
        selectedPlan = Plan.STARTER
        agreeToTerms = false
        errors = emptyMap()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        ProgressHeader(currentStep = currentStep, steps = allSteps)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            when (currentStep) {
                1 -> PersonalInfoStep(
                    firstName = firstName,
                    lastName = lastName,
                    errors = errors,
                    onFirstNameChange = { firstName = it },
                    onLastNameChange = { lastName = it },
                )
                2 -> AccountSetupStep(
                    email = email,
                    password = password,
                    confirmPassword = confirmPassword,
                    errors = errors,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onConfirmPasswordChange = { confirmPassword = it },
                )
                3 -> PlanSelectionStep(
                    selectedPlan = selectedPlan,
                    agreeToTerms = agreeToTerms,
                    errors = errors,
                    onPlanSelect = { plan ->
                        selectedPlan = plan
                        Altertable.shared?.track(event = "Plan Selected", properties = mapOf("plan" to plan.name.lowercase()))
                    },
                    onAgreeToTermsChange = { agreeToTerms = it },
                )
                4 -> WelcomeStep(
                    firstName = firstName,
                    lastName = lastName,
                    email = email,
                    plan = selectedPlan,
                    onGetStarted = {
                        Altertable.shared?.track(event = "Get Started Clicked")
                    },
                )
            }
        }

        if (currentStep < 4) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (currentStep > 1) {
                    TextButton(
                        onClick = { prevStep() },
                        colors = ButtonDefaults.textButtonColors(contentColor = SecondaryText),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Back")
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                Button(
                    onClick = { nextStep() },
                    colors = ButtonDefaults.buttonColors(containerColor = Blue),
                    shape = RoundedCornerShape(CornerRadiusSmall),
                ) {
                    Text(if (currentStep == 3) "Complete Signup" else "Continue")
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                TextButton(onClick = { handleRestart() }) {
                    Text("Restart", color = Blue)
                }
            }
        }
    }
}

@Composable
fun ProgressHeader(currentStep: Int, steps: List<SignupStep>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            Box(contentAlignment = Alignment.TopEnd) {
                Box(
                    modifier = Modifier
                        .size(StepIconSize)
                        .clip(CircleShape)
                        .background(if (currentStep >= step.id) Blue else BorderGray),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = step.icon,
                        contentDescription = step.title,
                        tint = if (currentStep >= step.id) Color.White else SecondaryText,
                        modifier = Modifier.size(20.dp),
                    )
                }
                if (currentStep > step.id) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = Green,
                        modifier = Modifier
                            .size(CheckBadgeSize)
                            .background(Color.White, CircleShape),
                    )
                }
            }

            if (index < steps.size - 1) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(ProgressLineHeight)
                        .background(if (currentStep > step.id) Blue else BorderGray),
                )
            }
        }
    }
}

@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = "",
    error: String? = null,
    isSecure: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder, color = SecondaryText) },
            isError = error != null,
            visualTransformation = if (isSecure) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = RoundedCornerShape(CornerRadiusSmall),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        if (error != null) {
            Text(
                text = error,
                color = ErrorRed,
                fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
fun PersonalInfoStep(
    firstName: String,
    lastName: String,
    errors: Map<String, String>,
    onFirstNameChange: (String) -> Unit,
    onLastNameChange: (String) -> Unit,
) {
    TrackScreenView("Personal Info")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(title = "Let's get started", subtitle = "Tell us a bit about yourself")
        FormField(
            label = "First Name",
            value = firstName,
            onValueChange = onFirstNameChange,
            placeholder = "Enter your first name",
            error = errors["firstName"],
        )
        FormField(
            label = "Last Name",
            value = lastName,
            onValueChange = onLastNameChange,
            placeholder = "Enter your last name",
            error = errors["lastName"],
        )
    }
}

@Composable
fun AccountSetupStep(
    email: String,
    password: String,
    confirmPassword: String,
    errors: Map<String, String>,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
) {
    TrackScreenView("Account Setup")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(title = "Create your account", subtitle = "Set up your login credentials")
        FormField(
            label = "Email Address",
            value = email,
            onValueChange = onEmailChange,
            placeholder = "Enter your email",
            error = errors["email"],
            keyboardType = KeyboardType.Email,
        )
        FormField(
            label = "Password",
            value = password,
            onValueChange = onPasswordChange,
            placeholder = "Create a password",
            error = errors["password"],
            isSecure = true,
        )
        FormField(
            label = "Confirm Password",
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            placeholder = "Confirm your password",
            error = errors["confirmPassword"],
            isSecure = true,
        )
    }
}

@Composable
fun PlanSelectionStep(
    selectedPlan: Plan,
    agreeToTerms: Boolean,
    errors: Map<String, String>,
    onPlanSelect: (Plan) -> Unit,
    onAgreeToTermsChange: (Boolean) -> Unit,
) {
    TrackScreenView("Choose Plan")
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        StepHeader(title = "Choose your plan", subtitle = "Select the plan that works best for you")

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Plan.entries.forEach { plan ->
                val isSelected = selectedPlan == plan
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(CornerRadiusSmall))
                        .background(if (isSelected) BlueLight else Color.White)
                        .border(
                            width = 1.dp,
                            color = if (isSelected) Blue else BorderGray,
                            shape = RoundedCornerShape(CornerRadiusSmall),
                        )
                        .clickable { onPlanSelect(plan) }
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("${plan.title} Plan", fontWeight = FontWeight.Bold)
                        Text(plan.description, color = SecondaryText, fontSize = 12.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("$${plan.price}", fontWeight = FontWeight.Bold)
                        Text("/month", color = SecondaryText, fontSize = 12.sp)
                    }
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onAgreeToTermsChange(!agreeToTerms) },
        ) {
            Checkbox(
                checked = agreeToTerms,
                onCheckedChange = onAgreeToTermsChange,
            )
            Text(
                text = "I agree to the Terms of Service and Privacy Policy",
                fontSize = 13.sp,
            )
        }

        if (errors["agreeToTerms"] != null) {
            Text(
                text = errors["agreeToTerms"]!!,
                color = ErrorRed,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
fun WelcomeStep(
    firstName: String,
    lastName: String,
    email: String,
    plan: Plan,
    onGetStarted: () -> Unit,
) {
    TrackScreenView("Welcome")
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = "Success",
            tint = Green,
            modifier = Modifier.size(WelcomeIconSize),
        )

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Welcome aboard!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Thanks $firstName, your account has been created successfully.",
                color = SecondaryText,
                textAlign = TextAlign.Center,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(CornerRadiusCard))
                .background(SurfaceGray)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Account Summary:", fontWeight = FontWeight.Bold)
            Text("Name: $firstName $lastName")
            Text("Email: $email")
            Text("Plan: ${plan.title}")
        }

        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Blue),
            shape = RoundedCornerShape(CornerRadiusSmall),
        ) {
            Text("Get Started", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        }
    }
}

@Composable
fun StepHeader(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = title,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            color = SecondaryText,
            textAlign = TextAlign.Center,
        )
    }
}
