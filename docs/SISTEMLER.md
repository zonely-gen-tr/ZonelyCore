# ZonelyCore sistemleri

[Tanıtıma dön](../README.md)

### Mağaza, kredi ve ödüller

**Web mağazası**

Panelde oluşturulan kategoriler, alt kategoriler ve ürünler oyun içi menülerde gösterilir. Oyuncular site kredileriyle alışveriş yapabilir. Ürün adı, açıklaması, seçenekleri ve oyun içindeki görünürlüğü panel üzerinden yönetilir; uzun açıklamalar lore satırlarına bölünür.

**Ürün seçenekleri ve paketler**

Bir ürünün farklı seçenekleri ayrı fiyat ve stok bilgileriyle sunulabilir. Paket ürünler tek satın alma altında işlenir. Satın alma sırasında güncel fiyat, stok ve teslimat koşulları kontrol edilir; bakiye ve sipariş kayıtları birlikte oluşturulur.

**Sunucular arası teslimat**

Ürünün hangi sunucularda, hangi konsol komutlarıyla verileceği panelden belirlenir. Yalnızca alışverişin yapıldığı sunucuya teslimat yapılabilir veya seçilen diğer sunuculara da gönderilebilir. Hedef sunucu kapalıysa teslimat kuyrukta bekler; çevrimiçi oyuncu şartı açıksa oyuncu o sunucuya girince işlenir.

**Kredi yönetimi**

Oyuncular kredi bakiyelerini oyun içinden görebilir. Yetkililer başka bir oyuncunun bakiyesini sorgulayabilir, kredi ekleyebilir, çıkarabilir veya belirli bir tutara ayarlayabilir. Mağaza ve açık artırma işlemleri aynı kredi sistemiyle çalışır.

**Kredi kuponları**

Krediler, başka bir oyuncuya verilebilen kupon eşyalarına dönüştürülebilir. Kupon oluşturulurken ilgili bakiye düşülür; kupon kullanıldığında kredi alıcıya aktarılır. Böylece kredi paylaşımı oyun içi bir eşya üzerinden yapılabilir.

**Açık artırma**

Oyuncular ellerindeki eşyayı kredi karşılığında satışa çıkarabilir. Açık artırma menüsünden ilanlar incelenebilir; satılan ve satın alınan eşyalar ayrı ekranlardan takip edilebilir. Panel ve oyun aynı açık artırma kayıtlarını kullanır.

**Zamanlı ödüller**

Oyuncular uygun oldukları ödülleri teslimat menüsünden alabilir. Ödülün yetki ve bekleme süresi koşulları kontrol edilir. Alınabilecek ödüller profil menüsünde veya ödül NPC'si üzerinden gösterilebilir.

**Son kredi yüklemeleri**

Kredi yükleyen oyuncular menüden görüntülenebilir. Son işlemler ile günlük, aylık, yıllık ve tüm zamanlara ait sıralamalar ayrı kategorilerde incelenebilir. Aynı veriler lobi hologramlarında ve PlaceholderAPI üzerinden de kullanılabilir.

### Hesap, giriş ve doğrulama

**Web hesabıyla giriş ve kayıt**

Oyuncular web panelinde oluşturdukları hesapla sunucuya giriş yapabilir; oyun içinden kayıt akışını da kullanabilir. Giriş tamamlanana kadar uygulanacak kısıtlamalar, deneme sınırları ve süreler yapılandırılabilir.

**Otomatik giriş**

Başarılı bir girişin ardından oturum, yapılandırılan süre ve IP koşullarına göre yeniden kullanılabilir. Aynı IP ile giriş, süre sınırı ve oturum geri yükleme davranışı ayrı ayarlarla yönetilir.

**Bossbar yönlendirmeleri**

Giriş, kayıt ve doğrulama adımlarında oyuncuya bossbar üzerinden bilgi verilir. Oyuncu hangi işlemi tamamlaması gerektiğini oyun ekranında takip edebilir; ilgili metinler dil dosyasından düzenlenir.

**E-posta doğrulama**

Oyuncu e-posta adresini oyun içinden tanımlayabilir ve doğrulama durumunu sorgulayabilir. Doğrulama e-postası, yeniden gönderme süresi ve hatırlatmalar yapılandırılabilir. E-posta göndermek için ilgili posta ayarlarının hazır olması gerekir.

**E-posta değiştirme**

Adres güncelleme işlemleri doğrulama akışı üzerinden yürütülür. Oyuncu yeni adresi tanımlayıp kendisine gönderilen doğrulamayı tamamlayabilir; e-posta ve hesap işlemleri panel kayıtlarıyla birlikte çalışır.

**İki aşamalı doğrulama — TOTP**

Oyuncu hesabına doğrulayıcı uygulama üzerinden tek kullanımlık kod ekleyebilir. Kurulum, onaylama, giriş kodu, devre dışı bırakma ve kurtarma işlemleri `/2fa` komutlarıyla yönetilir. Kod süresi ve girişte zorunlu tutulması yapılandırılabilir.

**Şifre sıfırlama**

Oyuncu `/passwordreset` ile şifre sıfırlama akışını başlatabilir. Süresi sınırlı bir bağlantı üzerinden web sitesine yönlendirilir ve yeni şifresini panelde belirler. Kullanılacak site adresi ve sıfırlama yolu ayarlanabilir.

**BungeeCord aktarımı**

Giriş veya kayıt tamamlandığında oyuncu, proxy üzerinde tanımlı hedef sunucuya gönderilebilir. Otomatik geri yüklenen oturumlarda aktarım yapılıp yapılmayacağı ve aktarım gecikmesi ayrıca seçilebilir.

**Launcher doğrulaması**

Sunucuya erişim, yapılandırılan ZonelyLauncher doğrulamasına bağlanabilir. Geçerli launcher oturumu bulunmayan bağlantılar için giriş davranışı ve bilgilendirme ayarlanabilir. Bu özellik ayrı launcher ve web API yapılandırması gerektirir.

### Lobi, profiller ve kişiselleştirme

**Menü açan NPC'ler**

Profil, mağaza, açık artırma, son krediler, raporlar, ödüller, destek ve turnuva menüleri için NPC oluşturulabilir. Aynı türden birden fazla NPC farklı kimliklerle yerleştirilebilir. İsim, görünüm, skin ve üzerindeki hologram satırları özelleştirilebilir.

**Hologramlar ve kredi sıralamaları**

Son kredi yüklemeleri ve sıralamalar lobi içinde gösterilebilir. Adlandırılmış hologramlar farklı konumlara yerleştirilebilir; desteklenen tıklama akışından ilgili kredi menüsüne ulaşılabilir. PlaceholderAPI değerleri hologram metinlerinde kullanılabilir.

**Web profili**

Oyuncunun panel hesabına bağlı bilgileri oyun içi profil ekranında gösterilir. Kredi bakiyesi, mağaza, zamanlı ödüller ve son yüklemeler gibi alanlara tek menüden erişilebilir. Menü öğeleri ve açıklamaları dil dosyası üzerinden düzenlenebilir.

**Gruplar ve yetkiler**

Oyuncu grupları, yetki düğümleri üzerinden sunucudaki yetki sistemiyle birlikte kullanılabilir. Grup adı ve rol bilgileri menülerde, oyuncu gösterimlerinde ve yer tutucularda kullanılabilir. Mağaza veya yönetim erişimi ilgili yetkilerle sınırlandırılır.

**Unvanlar, booster ve lig görünümü**

Unvan ve booster sistemleri için profil menüleri bulunur. Lig ekranı AlonsoLeagues entegrasyonuyla yapılandırılabilir; lig kimlikleri ve gösterim ayarları ilgili eklentiyle eşleştirilir. Bu entegrasyonlar kendi bağımlılıklarıyla birlikte kullanılır.

**Sohbet renkleri**

Yetkisi olan oyuncular sohbet renklerini menüden seçebilir. Seçilen renk oyuncunun sohbet gösterimine uygulanır; menüdeki seçenek ve açıklamalar özelleştirilebilir.

**Disguise / farklı kimlik**

Yetkili oyuncular isim, rol ve skin seçimleriyle oyun içinde farklı bir kimlik kullanabilir. Kimlik seçimi ve listeleme akışları ilgili menüler ve komutlarla yönetilir. Görünüm metinleri dil desteğiyle düzenlenebilir.

### Topluluk, destek ve yönetim

**Arkadaş sistemi**

Oyuncular arkadaşlık isteği gönderebilir, gelen isteği kabul veya reddedebilir ve arkadaş listesini menüden açabilir. Arkadaş kaldırma ve arkadaşlara özel mesaj gönderme komutları bulunur. Arkadaş sayısı sınırı sunucu ayarından değiştirilebilir.

**Canlı destek talepleri**

Oyuncular paneldeki destek kategorilerini kullanarak oyun içinden talep açabilir. Yetkililer talepleri listeleyebilir, cevaplayabilir, kapatabilir ve destek erişimini yönetebilir. Web sitesindeki ve oyun içindeki mesajlar aynı talep kayıtlarında tutulur.

**Oyuncu raporları**

Oyuncular kural ihlallerini `/report` ile bildirebilir. Yetkililer raporları ayrı bir menüde inceleyebilir, oyuncu ve olay bilgilerine ulaşabilir ve ilgili yönetim işlemlerini başlatabilir.

**Replay inceleme**

Rapor sistemindeki kayıtlar replay araçlarıyla incelenebilir. Yetkililer kaydı izleyerek olayın akışını değerlendirebilir; rapor ve ceza araçlarını birlikte kullanabilir. Kayıt ve oynatma davranışı rapor/replay modülünün ayarlarına bağlıdır.

**Ceza yönetimi**

Ban, IP ban, mute ve uyarı işlemleri komut veya menü üzerinden uygulanabilir. Süre ve sebep girilebilir; oyuncunun geçmiş cezaları görüntülenebilir. Ceza kayıtları web panelinde de yönetilebilir.

**Sohbet filtreleri**

Yasaklı kelimeler ve reklam içeriği için filtreler tanımlanabilir. Reklam anahtarları, küfür listeleri ve uygulanacak susturma süreleri yapılandırılır. Tekrarlanan ihlaller için farklı süreler belirlenebilir.

**Oyuncu analizi ve etkinlik kayıtları**

Oyuncuların oyun süresi ve günlük etkinlikleri panel istatistikleri için kaydedilebilir. Sohbet ve komut etkinliklerinin tutulması, verilerin ne sıklıkla yazılacağı ve saklama süresi ayrı ayarlarla yönetilir.

### E-spor, kulüpler ve turnuvalar

**Kulüp yönetimi**

Kulüp oluşturma, isim/etiket, üyeler, yönetim ve banner tasarımı için oyun içi ekranlar bulunur. Kulüpler turnuva kayıtlarında ve maç eşleşmelerinde kullanılır; kulüp durumu ilgili menülerden takip edilebilir.

**Turnuva akışı**

Kayıt, bekleme lobisi, eşleşme, canlı maç, kazanan ve temizlik aşamaları aynı sistemde yönetilir. Turnuvalar menüden oluşturulabilir ve takip edilebilir. Tekrarlama planı, kayıt süresi ve maç ayarları turnuva yapılandırmasına göre belirlenir.

**Maç ve izleyici yönetimi**

Katılımcılar başlangıç noktalarına yerleştirilir, maç durumu takip edilir ve izleyici akışı yönetilir. Maç bitiminde sonuçlar işlenir; ilgili oyuncu ve arena temizliği uygulanır. Turnuva ve kulüp bilgileri menülerde ve yer tutucularda gösterilebilir.

**Maç içi olaylar**

Zamanlı seviye sandıkları, özel kullanım eşyaları, dinamik hologramlar, meteor ve tren olayları maç akışına eklenebilir. Maç özeti ve kazanan kutlamaları bulunur. Olayların zamanlaması ve kullanılacak arena ayarları turnuva sisteminden yönetilir.

**Turnuva scoreboard'u ve istatistikleri**

E-spor maçları için skor tablosu ve başlık ayarları bulunur. Maç süresi, oyuncu sayısı, öldürme, ölüm, kırılan blok ve kulüp skorları PlaceholderAPI üzerinden diğer gösterimlere de aktarılabilir.

### Web bağlantısı ve entegrasyonlar

**Panel komutları / SecureSocket**

Panelin oyun sunucusuna ilettiği komutlar, yapılandırılan token ve bağlantı ayarlarıyla işlenir. Komutların hedefi paneldeki sunucu kayıtlarıyla belirlenir. Mağaza teslimatlarında hedef sunucunun kendi komut kuyruğu kullanılır.

**ChatBridge**

Web sitesi ile oyun sohbeti arasında bağlantı kurulur. Panelde seçilen sunucunun sohbeti, çevrimiçi oyuncuları ve komut akışı takip edilebilir. Sunucu kimliği, sorgu aralığı ve kayıt saklama süresi yapılandırılabilir.

**Envanter görüntüleri**

Oyuncunun giriş ve çıkışında envanter görüntüsü alınabilir. Panel bu kayıtları oyuncunun web profilinde envanter görünümü olarak kullanabilir. Sunucu eşlemesi ve kayıt davranışı ayrı ayarlardan yönetilir.

**MySQL / MariaDB ve PlaceholderAPI**

Panel verileri SQL veritabanı üzerinden kullanılır; kredi, profil, teslimat ve turnuva alanları yer tutucularla diğer eklentilere aktarılabilir. ZonelyCore kendi PlaceholderAPI genişletmelerini kaydeder. Aşağıda genel ve turnuva yer tutucularının listesi bulunur.

**Modüler yapı ve Java API**

Socket, kupon, açık artırma, rapor, son kredi, destek, arkadaş, giriş, launcher ve e-spor sistemleri ilgili ayarlardan yönetilebilir. Java API üzerinden kredi, kupon, ödül, son kredi, açık artırma ve socket servislerine erişilebilir.

**GitHub üzerinden güncelleme**

`/zc update`, bu deponun son kararlı Release kaydını kontrol eder. Yeni JAR arka planda indirilir ve doğrulanır; sunucu yeniden başlatıldığında uygulanır. [Güncelleme rehberi](GUNCELLEME.md).
