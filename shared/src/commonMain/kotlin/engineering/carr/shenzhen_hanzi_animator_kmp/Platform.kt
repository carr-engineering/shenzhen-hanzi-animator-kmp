package engineering.carr.shenzhen_hanzi_animator_kmp

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform