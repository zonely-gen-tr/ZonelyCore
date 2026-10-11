# ZonelyCore

**Minecraft sunucun ile Zonely web panelin arasında tek bağlantı.**

ZonelyCore; web mağazasını, oyuncu hesaplarını, kredileri, destek taleplerini ve yönetim araçlarını oyun içine taşır. Oyuncu sitedeki hesabıyla giriş yapar, aynı kredi bakiyesiyle alışveriş yapar ve satın aldığı ürünü ilgili sunucuda teslim alır. Yetkililer raporları, cezaları ve destek taleplerini oyun içinden takip edebilir.

Lobi NPC'lerinden turnuva maçlarına, e-posta doğrulamasından launcher girişine kadar farklı sistemler aynı çekirdekte toplanır. Menü metinleri, açıklamalar ve mesajlar dil dosyasından düzenlenebilir; kullanılmayan modüller yapılandırmadan kapatılabilir.

[JAR indir](https://github.com/zonely-gen-tr/ZonelyCore/releases/latest) · [Kurulum](docs/KURULUM.md) · [Mağaza rehberi](docs/MAGAZA.md) · [Geliştirici API](docs/ZonelyCoreAPI.md) · [SpigotMC](https://www.spigotmc.org/resources/zonelycore-one-core-for-minecraft.129652/) · [Destek](https://github.com/zonely-gen-tr/ZonelyCore/issues)

## Sistemler

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

`/zc update`, bu deponun son kararlı Release kaydını kontrol eder. Yeni JAR arka planda indirilir ve doğrulanır; sunucu yeniden başlatıldığında uygulanır. [Güncelleme rehberi](docs/GUNCELLEME.md).

## Diller

![Desteklenen 19 dil: Türkçe, İngilizce, Almanca, İspanyolca, Fransızca, Çekçe, Hintçe, Macarca, İtalyanca, Japonca, Norveççe Bokmål, Felemenkçe, Lehçe, Brezilya Portekizcesi, Rumence, Rusça, İsveççe, Vietnamca ve Çince](docs/assets/zonelycore-languages.svg)

Menü başlıkları, eşya isimleri, lore satırları ve sistem mesajları `languages.yml` üzerinden düzenlenir. Varsayılan dili Türkçe yapmak için:

```yaml
default: tr_TR
```

<details>
<summary>Dil kodları</summary>

| Dil | Kod | Dil | Kod |
| --- | --- | --- | --- |
| Türkçe | `tr_TR` | İngilizce | `en_US` |
| Almanca | `de_DE` | İspanyolca | `es_ES` |
| Fransızca | `fr_FR` | Çekçe | `cs_CZ` |
| Hintçe | `hi_IN` | Macarca | `hu_HU` |
| İtalyanca | `it_IT` | Japonca | `ja_JP` |
| Norveççe — Bokmål | `nb_NO` | Felemenkçe | `nl_NL` |
| Lehçe | `pl_PL` | Portekizce — Brezilya | `pt_BR` |
| Rumence | `ro_RO` | Rusça | `ru_RU` |
| İsveççe | `sv_SE` | Vietnamca | `vi_VN` |
| Çince | `zh_CN` | | |

</details>

## Gereksinimler ve kurulum

ZonelyCore, **Zonely Minecraft web paneliyle birlikte** çalışmak üzere geliştirilmiştir. Panel kurulumu, veritabanı ve oyun sunucusu kaydı hazır olmalıdır. Özellikleri kullanmak için ilgili modülü ve bağlantı ayarlarını etkinleştirin.

| Gereksinim | Açıklama |
| --- | --- |
| Zonely web paneli | Hesap, mağaza, sunucu ve yönetim kayıtları. |
| MySQL / MariaDB | Panel veritabanına erişim. |
| [ProtocolLib](https://www.spigotmc.org/resources/protocollib.1997/) | Sunucu sürümüne uygun paket/metadata desteği. |
| [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/) | Yer tutucular ve diğer gösterimlerle entegrasyon. |
| Uygun Java sürümü | Kullanılan Minecraft sunucusunun gerektirdiği Java. |

1. ProtocolLib ve PlaceholderAPI'yi sunucuya kur.
2. [ZonelyCore JAR'ını indir](https://github.com/zonely-gen-tr/ZonelyCore/releases/latest) ve `plugins/` içine koy.
3. İlk çalıştırmada oluşan `plugins/ZonelyCore/config.yml` dosyasına panel veritabanı bilgilerini yaz.
4. Panelde bu sunucu için tanımlanan token değerini `web-token` alanına ekle.
5. Kullanacağın modülleri ve paneldeki ürün/komut eşlemelerini ayarla; sunucuyu yeniden başlat.

**Test ortamı:** Paper 1.20.4 build 499 · Java 21 · ProtocolLib 5.4.0 · PlaceholderAPI 2.11.6.

[Ayrıntılı kurulum](docs/KURULUM.md) · [Mağaza ayarları](docs/MAGAZA.md)

## Mağaza sunucu ayarı

```yaml
web-token: "BU_SUNUCUYA_OZEL_PANEL_TOKENI"
store:
  # false: Bu sunucu. true: Panelde seçilen diğer sunucular da.
  send-to-other-servers: false
  # Oyuncu hedef sunucuya girene kadar teslimatı bekletir.
  require-player-online: true
```

Mağaza için ayrıca `store.server-id` girilmez; sunucu kendi `web-token` değeriyle panel kaydına eşleşir. Her sunucuya farklı token tanımlanmalıdır. Diğer sunuculara teslimat için o sunucularda da ZonelyCore çalışmalıdır. Panelde kategori, üst kategoriler ve ürün/seçenek için **Oyunda Göster** ayarını etkinleştirin.

## Komutlar

| Komut | Açıklama |
| --- | --- |
| `/zc` | Yönetim yardımı. |
| `/zc reload` | Dosyaları yeniden yükleme. |
| `/zc update` | GitHub'dan sürüm kontrolü ve indirme. |
| `/zc npc spawn <tür> [id]` | Lobi NPC'si oluşturma. |
| `/zc npc remove <tür> [id]` | Lobi NPC'sini kaldırma. |
| `/zc hologram lastcredits [id]` | Son kredi hologramını yerleştirme. |
| `/webprofile` | Oyuncu profil menüsü. |
| `/store` · `/magaza` · `/zonelystore` | Mağaza menüsü. |
| `/credit [oyuncu]` | Kredi bakiyesini sorgulama. |
| `/credit set <oyuncu> <tutar>` | Bakiyeyi belirleme. |
| `/credit add <oyuncu> <tutar>` | Kredi ekleme. |
| `/credit remove <oyuncu> <tutar>` | Kredi çıkarma. |
| `/creditvoucher <oyuncu> <tutar>` | Kredi kuponu oluşturma. |
| `/lastcredits` | Son kredi yüklemeleri ve sıralamalar. |
| `/auction <tutar>` | Eldeki eşyayı açık artırmaya koyma. |
| `/auctions` | Açık artırma menüsü. |
| `/report <oyuncu>` | Oyuncu raporlama. |
| `/reports` | Yetkili rapor menüsü. |
| `/punish` | Ceza menüsü ve alt komutları. |
| `/punish ban/ipban/mute/warning <oyuncu> <süre> <sebep>` | Ceza uygulama. |
| `/punish unban/unmute/unwarning <oyuncu>` | İlgili cezayı kaldırma. |
| `/support` | Destek talepleri. |
| `/friends` | Arkadaş menüsü. |
| `/friends add/accept/deny/remove <oyuncu>` | Arkadaşlık isteği ve liste yönetimi. |
| `/friends msg <oyuncu> <mesaj>` | Arkadaşa özel mesaj. |
| `/disguise` | Farklı kimlik menüsü. |
| `/club` · `/club help` | Kulüp menüsü ve komut yardımı. |
| `/esports` | Turnuva menüsü ve yönetimi. |
| `/login` · `/register` | Giriş ve kayıt. |
| `/email <set\|resend\|status>` | E-posta işlemleri. |
| `/passwordreset` | Şifre sıfırlama. |
| `/2fa <setup\|confirm\|cancel\|disable\|code\|recovery>` | İki aşamalı doğrulama. |

NPC türleri: `web-profile`, `auctions`, `last-credits`, `reports`, `deliveries`, `web-store`, `supports`, `tournaments`.

```text
/zc npc spawn web-store magaza
/zc npc spawn web-profile profil
/zc npc spawn tournaments turnuvalar
/zc hologram lastcredits krediler
```

## Yetkiler

| Yetki | Kullanım |
| --- | --- |
| `zcore.admin` | ZonelyCore yönetimi. |
| `zonely.auction` | Açık artırma kullanımı. |
| `zonely.auction.manage` | Açık artırma yönetimi. |
| `zonely.reports.view` | Raporları görüntüleme. |
| `zonely.punish` | Ceza komutları. |
| `zonely.punish.ban` · `zonely.punish.ipban` | Ban ve IP ban. |
| `zonely.punish.mute` | Susturma. |
| `zonely.punish.warning` | Uyarı. |
| `zonely.punish.history` | Ceza geçmişi. |
| `zonely.punish.notify` | Ceza bildirimleri. |
| `zonely.support.staff` | Yetkili destek paneli. |
| `zonely.support.ban` | Destek erişimini yönetme. |
| `zcore.cmd.disguise` · `zcore.cmd.disguiselist` | Disguise ve kimlik listesi. |
| `esports.manage` | Turnuva yönetimi. |

## PlaceholderAPI

Yer tutucular scoreboard, menü ve hologram eklentilerinde kullanılabilir. Son kredi alanlarında `<sıra>`, `1` gibi bir kayıt sırasıdır. Gösterilecek kayıt veya etkin turnuva yoksa ilgili değer boş dönebilir.

### Genel ve kredi alanları

| Yer tutucu | Değer |
| --- | --- |
| `%ZonelyCore_cash%` | Kredi bakiyesi. |
| `%ZonelyCore_perm%` | Oyuncu rolü/grubu. |
| `%ZonelyCore_playername%` | Gösterilen oyuncu adı. |
| `%ZonelyCore_status_delivery%` | Bekleyen zamanlı ödül bilgisi. |
| `%ZonelyCore_lastcredit_<sıra>%` | İlgili kaydın oyuncu adı. |
| `%ZonelyCore_lastcredit_<sıra>_display%` | Oyuncu ve yükleme tutarı. |
| `%ZonelyCore_lastcredit_<sıra>_name%` | Oyuncu adı. |
| `%ZonelyCore_lastcredit_<sıra>_amount%` | Yüklenen kredi. |
| `%ZonelyCore_lastcredit_<sıra>_date%` | Yükleme tarihi. |
| `%ZonelyCore_lastcredit_<sıra>_time%` | Yükleme zamanı. |

<details>
<summary>Turnuva, kulüp ve maç yer tutucuları</summary>

```text
%zonely_tournament_name%
%zonely_tournament_state%
%zonely_tournament_event%
%zonely_tournament_start%
%zonely_tournament_end%
%zonely_tournament_club_count%
%zonely_tournament_countdown%
%zonely_tournament_countdown_seconds%
%zonely_tournament_register_open%
%zonely_tournament_repeatable%
%zonely_tournament_repeat_days%
%zonely_tournament_repeat_mode%
%zonely_club_name%
%zonely_club_tag%
%zonely_club_seed%
%zonely_club_status%
%zonely_next_match_round%
%zonely_next_match_slot%
%zonely_next_match_a%
%zonely_next_match_b%
%zonely_active_match_id%
%zonely_match_time%
%zonely_match_id%
%zonely_match_players%
%zonely_match_kills%
%zonely_match_deaths%
%zonely_match_blocks%
%zonely_match_clubkills%
%zonely_match_clubkills_<TAG>%
```

Maç istatistiklerinin bir bölümü oyuncuya özeldir. Kulüp etiketiyle kullanılan alanlarda `<TAG>` yerine ilgili kulübün etiketi yazılır.

</details>

## Geliştirici API

ZonelyCore etkinleştirildikten sonra `ZonelyCoreAPI.get()` ile API'ye erişilebilir. Kendi eklentinizin bağımlılıklarına `ZonelyCore` ekleyin.

```java
import dev.zonely.whiteeffect.api.ZonelyCoreAPI;

ZonelyCoreAPI api = ZonelyCoreAPI.get();
long bakiye = api.cash().getBalance("Oyuncu");
```

| Servis | Kullanım |
| --- | --- |
| `api.cash()` | Bakiye sorgulama ve kredi işlemleri. |
| `api.vouchers()` | Kupon oluşturma, okuma ve kullanma. |
| `api.deliveries()` | Zamanlı ödüller ve alma durumu. |
| `api.lastCredits()` | Son yüklemeler ve sıralama önbelleği. |
| `api.auctions()` | Açık artırma ekranları ve veri erişimi. |
| `api.socket()` | Panel bağlantısının durumu ve kontrolü. |

[API rehberi](docs/ZonelyCoreAPI.md) · [Kaynaktan derleme](docs/GELISTIRME.md)

## Lisans ve destek

Zonely tarafından geliştirilen kod [MIT lisansı](LICENSE) altındadır. [Üçüncü taraf bileşenler](THIRD_PARTY_NOTICES.md) kendi lisanslarına tabidir. Web paneli, launcher ve ayrı Zonely ürünleri bu deponun lisansına dahil değildir.

[Zonely](https://zonely.gen.tr) · [SpigotMC](https://www.spigotmc.org/resources/zonelycore-one-core-for-minecraft.129652/) · [GitHub Releases](https://github.com/zonely-gen-tr/ZonelyCore/releases) · [Hata bildirimi](https://github.com/zonely-gen-tr/ZonelyCore/issues)

WhiteEffect · Orçun Dönmez · Zonely
