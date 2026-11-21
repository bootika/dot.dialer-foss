package dev.goodwy.rphone
import org.koin.core.context.GlobalContext
import dev.goodwy.rphone.modal.`interface`.ICallRepository
import ro.dotdesign.updates.*
internal object AppUpdates {
    fun create(activity: MainActivity): UpdateHandle {
        val runtime = UpdateRuntime.get(activity,"https://dialer.dotdesign.ro/updates.php","ro.dotdesign.dotdialer","MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAoLL6d0YKFTntoxJAeEkI/bdgJinGutwkMNLtJnuJZOAhwDyh9PBETmGfNjxjpr1/eul2MeST1FrryZKcdaJyYTtBZ2sLGhl3EEXbbb6N8wuDkp85Hw7szh+u9v5SuwA4hSw4KSZ6E1WBPEVfWsKYQUgk4/lzVrLCwEeIUmRVuSb+bPhIuafFX/hbrgSNmI53IiQCzsFodhmCeUdMdAMYBq3wsv2JtyPSJTyqalSyM5ccKmmgaGmR5nSjl5kkhvGEp7TDm1vVi68e8aZWm3b8ypdGh87jpJ6KNac5/5A2Dhx4c5GjJ6asJsbFu5l8isa76mLj0cEEUZJQBCePAx0w10D+fk+NUUgK0nBzMdiur0Az9lEadPHRfWeGLT6a5S3dPdJIrL9YvHe+TNAyM7LQZnQK5DFuSaf7S+nTsXeofayF0i96AAuh7l8Zn0mzHdjh/qA0ruztEI+5YsqRKdeulSgS+LpCpaZBpvUkUBeS6VGei+L+8KAntKghE1THUgB5AgMBAAE=")
        return PlayUpdateController(activity,runtime,{
            activity.isHandlingPhoneNavigation() ||
                GlobalContext.get().get<ICallRepository>().allCalls.value.isNotEmpty()
        },R.string.app_update_title,R.string.app_update_required,R.string.app_update_now,R.string.app_update_later,
            R.string.app_update_optional,0xff00ac78.toInt(),R.string.app_update_news)
    }
}
