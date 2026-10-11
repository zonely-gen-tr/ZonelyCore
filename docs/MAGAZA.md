# Mağaza ve sunucular arası teslimat

## Katalog görünürlüğü

Ürünlerin ve kategorilerin panelde **Oyunda Göster** seçeneğini etkinleştirin. Üst kategoriler de görünür olmalıdır. Bir üründe birden fazla seçenek varsa ilgili seçenek ayarını kontrol edin. Gizli katalog kayıtları eklenti tarafından otomatik açılmaz.

Katalog aralıklarla yenilenir. `/zc reload` ile yeniden yükleyebilir veya boş katalog menüsündeki yenileme öğesini kullanabilirsiniz. `/store` başka eklentiyle çakışıyorsa `/zonelycore:store`, `/zonelystore` veya `/magaza` deneyin.

## Teslimat hedefi

`web-token` genel kurulum için zorunlu değildir; mağaza satın alma ve teslimatını paneldeki sunucuya bağlamak için doldurulur.

Mağaza için ayrıca `store.server-id` yazılmaz. `web-token`, panelin `plugin_servers.password` alanıyla eşleşerek bu sunucuyu belirler. Aynı token birden fazla sunucu kaydında kullanılmamalıdır.

```yaml
web-token: "BU_SUNUCUYA_OZEL_PANEL_TOKENI"
store:
  # false: Bu sunucu. true: Ürün için panelde seçilen diğer sunucular da.
  send-to-other-servers: false
  require-player-online: true
```

Panelde ürün seçeneğine hedef sunucuları ve her sunucuda çalışacak konsol komutlarını ekleyin. Örneğin yalnızca test ortamında `say Teslimat %player%` komutuyla akışı görebilirsiniz. Gerçek ürün için eşya/rütbe sağlayan eklentinin doğru konsol komutunu kullanın. Hedef sunucuların her birinde ZonelyCore kurulu olmalıdır.

`send-to-other-servers: false` iken bu sunucuya ait teslimat komutu yoksa satın alma reddedilir ve kredi düşülmez. `true` iken panelde seçili diğer sunuculara da kuyruk kaydı oluşturulur. Bu ayar yeni siparişleri etkiler.

`require-player-online: true` iken oyuncu hedef sunucuya girene kadar komut bekler. `false`, yalnızca çevrimdışı oyunculara da uygulanabilen komutlarla kullanılmalıdır.

## Satın alma ve hata durumları

Bakiye, stok, sipariş ve teslimat kayıtları SQL işlemi içinde oluşturulur. Komut yürütme daha sonra hedef sunucuda yapılır. Kapalı sunucunun teslimatı bekler. Komut hataları başarısız olarak kaydedilir. İşlenirken kesintiye uğrayan kayıtlar otomatik tekrar yerine inceleme gerektirebilir; bir komutun iki defa çalışmaması için önce kayıtları kontrol edin.

Sınırsız stok, oyuncu başına tek satın alma anlamına gelmez. Oyuncu yeterli kredisi varsa yeni sipariş verebilir; her yeni satın alımda ücret alınır. Aynı işlem kimliğinin tekrar işlenmesi ise yeni sipariş oluşturmaz. Sıfır fiyatlı ürünler panelde ücretsiz yapılandırılabilir.
