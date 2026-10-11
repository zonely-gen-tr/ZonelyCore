# Kurulum

ZonelyCore, Zonely Minecraft web paneliyle birlikte kullanılır. Panel veritabanı şeması ve sunucu kaydı hazır olmalıdır.

1. Sunucu sürümüne uygun ProtocolLib ve PlaceholderAPI kurun. Bu düzeltmeler Paper 1.20.4 build 499 / Java 21 / ProtocolLib 5.4.0 / PlaceholderAPI 2.11.6 ortamında test edilmiştir.
2. ZonelyCore JAR'ını `plugins/` içine koyun. İlk çalıştırmada oluşan `plugins/ZonelyCore/config.yml` dosyasını düzenleyin.
3. `database.mysql` altında `host`, `port`, `name`, `username`, `password` değerlerini kendi veritabanınızla doldurun. Varsayılan bilgiler örnektir. `database.require-mysql: true` iken erişilebilir MySQL/MariaDB gereklidir.
4. Panelde bu oyun sunucusu için oluşturduğunuz token değerini `web-token` alanına yazın. Ağdaki her sunucu için farklı token kullanın.
5. İhtiyaç duyduğunuz sistemleri `modules` altında etkinleştirin. Giriş, launcher, turnuva ve destek gibi sistemlerin kendi ek ayarlarını da kontrol edin.
6. `languages.yml` içindeki `default` alanını `tr_TR` yaparak Türkçe varsayılan dil seçebilirsiniz. Menü başlıkları ve lore aynı dosyadan özelleştirilir.
7. Sunucuyu yeniden başlatın; `/zc`, `/webprofile` ve `/store` ile kontrol edin.

Veritabanı ve token değerleri sunucuya özeldir. Örnek yapılandırmadaki boş değerleri yayınlanan kaynakta kişisel bilgilerle değiştirmeyin.

[Mağaza ve sunucular arası teslimat](MAGAZA.md) · [GitHub güncellemeleri](GUNCELLEME.md)
