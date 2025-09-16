# 💰 MyFinanceHub - Personal Finance Management App

A beautiful, professional Android finance management app built with Jetpack Compose, featuring Zoho Catalyst integration and a stunning blue theme.

## ✨ Features

### 🔐 **Authentication**
- Secure user authentication with Zoho Catalyst
- Sign up with email verification
- Professional user profile management
- Real-time user data synchronization

### 💼 **Financial Management**
- **Transaction Tracking**: Add, edit, and categorize income/expenses
- **Budget Planning**: Set monthly budgets by category
- **Real-time Analytics**: Comprehensive budget vs spending analysis
- **Smart Insights**: AI-powered spending patterns and recommendations

### 📊 **Advanced Analytics**
- **Budget Performance**: See exactly how much you've saved per category
- **Spending Trends**: Monthly and category-wise breakdown
- **Visual Charts**: Beautiful pie charts and progress indicators
- **Savings Tracking**: Monitor financial goals and achievements

### 🎨 **Professional Design**
- **Blue Theme**: Trustworthy, professional color scheme
- **Material Design 3**: Modern, accessible interface
- **Dark/Light Modes**: Automatic theme switching
- **Responsive UI**: Perfect scrolling with keyboard handling

## 🛠️ Technology Stack

### **Frontend**
- **Jetpack Compose** - Modern declarative UI
- **Material Design 3** - Professional design system
- **MVVM Architecture** - Clean, maintainable code structure
- **Navigation Component** - Smooth app navigation

### **Backend & Data**
- **Zoho Catalyst SDK** - Cloud backend integration
- **Room Database** - Local data persistence
- **Kotlin Coroutines** - Asynchronous programming
- **StateFlow/LiveData** - Reactive UI updates

### **Features**
- **Real-time Sync** - Cloud data synchronization
- **Offline Support** - Local database caching
- **Theme Management** - Dynamic theme switching
- **Currency Formatting** - Localized money display

## 🎨 Design Philosophy

### **Professional Blue Theme**
- **Primary**: Deep blue (`#0D47A1`) for trust and reliability
- **Accent**: Vibrant blue (`#1E88E5`) for interactive elements
- **Success**: Green (`#4CAF50`) for income and positive actions
- **Error**: Red (`#E53935`) for expenses and warnings

### **User Experience**
- **Accessibility First**: High contrast ratios, readable fonts
- **Mobile Optimized**: Touch-friendly interfaces, smooth animations
- **Keyboard Support**: Proper scrolling when keyboard appears
- **Consistent Design**: Unified visual language across all screens

## 🚀 Getting Started

### **Prerequisites**
- Android Studio Arctic Fox or later
- Android SDK 24+ (Android 7.0)
- Kotlin 1.8+
- Gradle 8.0+

### **Setup**
1. **Clone the repository**
   ```bash
   git clone https://github.com/your-username/MyFinanceHub.git
   cd MyFinanceHub
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned repository

3. **Configure Zoho Catalyst**
   - Create a Zoho Catalyst app
   - Update `app_configuration_development.properties` with your app details
   - Add your configuration files as needed

4. **Build and Run**
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

## 📱 Screenshots

### Home Screen
- Current balance card with beautiful blue gradient
- Income/expense tracking with clear visual indicators
- Recent transactions with category icons

### Budget Planning
- Monthly budget creation and management
- Progress tracking with visual indicators
- Category-wise budget allocation

### Analytics Dashboard
- Comprehensive spending analysis
- Budget vs actual performance
- Savings achievements and overspending alerts

### Profile Management
- User account information
- Theme selection (Light/Dark/System)
- Settings and preferences

## 🏗️ Project Structure

```
app/src/main/java/com/ssk/myfinancehub/
├── auth/                   # Authentication management
├── data/
│   ├── dao/               # Database access objects
│   ├── database/          # Room database setup
│   ├── model/             # Data models
│   └── repository/        # Data repositories
├── navigation/            # App navigation
├── ui/
│   ├── navigation/        # Bottom navigation
│   ├── screens/           # All app screens
│   ├── theme/             # Theme and styling
│   └── viewmodel/         # ViewModels for MVVM
└── utils/                 # Utility classes
```

## 🎯 Key Features Detail

### **Budget Analytics**
```kotlin
// Real-time budget vs spending analysis
data class CategoryBudgetAnalysis(
    val category: String,
    val budgetAmount: Double,
    val spentAmount: Double,
    val savedAmount: Double,
    val overspentAmount: Double,
    val savingsPercentage: Float,
    val isOverBudget: Boolean
)
```

### **Theme System**
```kotlin
// Professional blue color scheme
val Primary40 = Color(0xFF0D47A1)      // Deep blue
val Secondary40 = Color(0xFF1E88E5)    // Vibrant blue
val IncomeGreen = Color(0xFF4CAF50)    // Income indicator
val ExpenseRed = Color(0xFFE53935)     // Expense indicator
```

## 🔄 Version Control

### **Git Best Practices**
- Comprehensive `.gitignore` for Android projects
- Semantic commit messages
- Feature branch workflow
- Regular commits with meaningful descriptions

## 📝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🤝 Support

For support, email senthilkumar30998@gmail.com or create an issue in this repository.

## 🚀 Future Enhancements

- [ ] Investment tracking
- [ ] Bill reminders
- [ ] Export to PDF/Excel
- [ ] Multiple currency support
- [ ] Backup and restore
- [ ] Biometric authentication
- [ ] Widgets for home screen
- [ ] Advanced reporting

---

**Made with ❤️ and Jetpack Compose**

*A professional finance management solution for modern Android users.*
