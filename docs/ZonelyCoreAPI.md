# ZonelyCore API

Kendi eklentinizin `plugin.yml` dosyasına `depend: [ZonelyCore]` ekleyin. API'yi ZonelyCore etkinleştirildikten sonra alın:

```java
import dev.zonely.whiteeffect.api.ZonelyCoreAPI;

ZonelyCoreAPI api = ZonelyCoreAPI.get();
long bakiye = api.cash().getBalance("Oyuncu");
```

## Kullanılabilir servisler

| Servis | İşlev |
| --- | --- |
| `api.cash()` | Bakiye sorgulama, ekleme, çekme, değiştirme ve önbellek yönetimi. |
| `api.vouchers()` | Kredi kuponu oluşturma, okuma ve kullanma. |
| `api.deliveries()` | Zamanlı ödülleri listeleme, durumunu sorgulama ve alma. |
| `api.lastCredits()` | Son kredi yüklemeleri ve sıralama önbelleği. |
| `api.auctions()` | Açık artırma menüleri ve veri erişimi. |
| `api.socket()` | Panel bağlantısının durumunu sorgulama, başlatma ve yeniden başlatma. |

## Kredi işlemleri

```java
long yeniBakiye = api.cash().addBalance("Oyuncu", 250);
long kalan = api.cash().withdraw("Oyuncu", 100);
api.cash().setBalance("Oyuncu", 5000);
```

Değişiklik yapan metotlar `dev.zonely.whiteeffect.api.exception.CreditOperationException` fırlatabilir. Kredi metotları senkrondur; bir async API döndürmez. Çağrıları kendi iş parçacığı düzeninize göre planlayın. Bukkit oyuncu, envanter ve menü işlemlerini ana iş parçacığında yapın.

## Son kredi yüklemeleri

```java
import dev.zonely.whiteeffect.store.LastCreditLeaderboardService;
import dev.zonely.whiteeffect.store.LastCreditsMenuManager;
import java.util.List;

List<LastCreditLeaderboardService.LeaderboardEntry> liste =
    api.lastCredits().getLeaderboard(LastCreditsMenuManager.Category.RECENT);
long yenileme = api.lastCredits()
    .getLastLeaderboardRefresh(LastCreditsMenuManager.Category.RECENT);
```

`getRecentTopups()`, `getRecentTopup(int)` ve `getRecentTopupsLastUpdated()` metotları PlaceholderAPI için kullanılan son yükleme önbelleğine erişir.

## Modüller

`isSocketEnabled()`, `areVouchersEnabled()`, `areAuctionsEnabled()`, `areReportsEnabled()` ve `areLastCreditsEnabled()` ile ilgili modülün durumunu kontrol edebilirsiniz. Bir servise erişmek, modülün etkin olduğunu göstermez.

İmzaların tamamı için [API kaynaklarına](../src/main/java/dev/zonely/whiteeffect/api) bakın.
