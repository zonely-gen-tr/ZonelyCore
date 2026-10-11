# Üçüncü taraf bildirimleri

Kök dizindeki MIT lisansı Zonely tarafından geliştirilen kod için geçerlidir. Aşağıdaki bileşenler kendi lisanslarını korur:

| Bileşen | Konum | Lisans / kaynak |
| --- | --- | --- |
| PacketWrapper | `src/main/java/com/comphenix/packetwrapper` | GNU LGPL v2 veya sonrası; kaynak başlıkları korunmuştur. [Upstream](https://github.com/dmulloy2/PacketWrapper). |
| JSON.simple | `src/main/java/org/json/simple` | Apache License 2.0. [Upstream](https://github.com/fangyidong/json-simple). |
| jBCrypt | `src/main/java/dev/zonely/whiteeffect/auth/crypto/BCrypt.java` | Damien Miller, ISC izin bildirimi kaynak dosyasında korunmuştur. [Upstream](https://www.mindrot.org/projects/jBCrypt/). |

Maven bağımlılıkları ve çalışma anında kullanılan sunucu/eklenti API'leri kendi lisanslarına tabidir. Spigot, ProtocolLib, PlaceholderAPI, MongoDB sürücüleri ve yerel Zonely entegrasyon JAR'ları bu kaynak paketinde dağıtılmaz. Derleme bağımlılıkları `pom.xml` ve `docs/GELISTIRME.md` içinde listelenir.

Zonely web paneli, launcher ve diğer ayrı ürünler bu deponun MIT lisansına dahil değildir.
