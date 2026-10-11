# Kaynaktan derleme ve testler

Maven ve uygun JDK kurun. Kaynak/hedef bytecode seviyesi Java 8'dir; son yerel derleme Java 21 ile yapılmıştır. Çalıştıracağınız Minecraft sürümünün Java gereksinimi ayrıca geçerlidir.

`pom.xml` bazı bağımlılıkları `systemPath` üzerinden okur. Aşağıdaki dosyaları yetkili/orijinal kaynaklarından edinip `libs/` içine koyun; bu JAR dosyaları kaynak deposuna dahil edilmez:

- `spigot.jar` — Spigot 1.8.8 tam sunucu JAR'ı (BuildTools ile).
- `spigot-1.16.5.jar` — 1.16.5 API/sunucu sınıfları.
- `ProtocolLib.jar` — bu sürümde 5.4.0 kullanıldı.
- `PlaceholderAPI-2.11.6.jar`.
- `wMysteryBox.jar` — ilgili Zonely entegrasyonunun derleme API'si.
- `bson-4.11.0.jar`, `mongodb-driver-core-4.11.0.jar`, `mongodb-driver-sync-4.11.0.jar`.

```text
mvn package
```

Dağıtılacak eklenti: `target/ZonelyCore-5.0.1-shaded.jar`. Yayın için `ZonelyCore-5.0.1.jar` adı kullanılabilir. Normal ve shaded JAR'ı aynı sunucuya birlikte koymayın.

Standart çalıştırmada veritabanı entegrasyon sınıfı atlanır. Mağaza entegrasyon testleri yalnızca `jdbc:mariadb://127.0.0.1:33307/zonely_store_test` adresini kabul eder; farklı veya canlı veritabanı adresi reddedilir. Bu testler tabloları yeniden oluşturur ve yalnızca ayrı bir yerel test veritabanında çalıştırılmalıdır.

MariaDB'yi `127.0.0.1:33307` üzerinde, test için `root` / `zonely_local_test_only` hesabıyla hazırlayın. PowerShell:

```powershell
$env:ZONELY_STORE_TEST_URL = 'jdbc:mariadb://127.0.0.1:33307/zonely_store_test'
mvn test
Remove-Item Env:ZONELY_STORE_TEST_URL
```

Test fixture'ı tamamen yapay ürün/kategori verisi kullanır. Son tam yerel çalıştırma: **37 test, 0 hata, 0 atlanan test**. GitHub Release testleri ağdan bağımsız HTTP yanıtlarıyla sürüm seçimi, indirme, checksum, eksik dosya ve yeniden denemeyi doğrular.

GitHub Actions yalnızca kaynak paketinin dosya, örnek ayar ve sürüm kontrollerini çalıştırır. Maven derlemesi ve oyun içi testler, yukarıdaki yerel bağımlılıklarla ayrıca çalıştırılır.
