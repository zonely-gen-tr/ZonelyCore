# GitHub güncellemeleri ve yayın hazırlığı

## Sunucu yöneticisi

Güncelleme kaynağı: `https://api.github.com/repos/zonely-gen-tr/ZonelyCore/releases/latest`.

Başlangıçta sürüm kontrolü arka planda yapılır. Yeni sürüm bulunduğunda yetkililere bildirim gösterilir. `zcore.admin` yetkisiyle `/zc update` yazıldığında yeni sürüm varsa JAR indirilir; ilk kontrol başarısız olmuşsa yeniden kontrol edilir. İndirme tamamlandığında sunucuyu yeniden başlatın.

Dosya `plugins/ZonelyCore/update/` yerine Bukkit'in `getUpdateFolderFile()` ile bildirdiği klasöre, çalışan ZonelyCore JAR'ının mevcut adıyla hazırlanır. Varsayılan klasör genellikle `plugins/update/` olur. Farklı bir dosya adıyla kurduysanız güncelleme aynı adı kullanır.

Ağ isteği ve indirme ana iş parçacığını bekletmez. Bağlantı/okuma zaman aşımı uygulanır. Dosya boyutu, GitHub'ın sağladığı SHA-256 özeti mevcutsa bu özet ve `plugin.yml` içindeki `name`/`version` alanları doğrulanır. Başarısız indirmede `.part` dosyası kaldırılır; daha önce hazırlanmış dosya korunur.

Eski sürümler GitHub'a yönlendirilemez; içlerindeki güncelleyici eski web adreslerini kullanır. İlk GitHub destekli sürümü elle kurun. Sonraki güncellemeler bu sistem üzerinden alınabilir.

## Yayını hazırlayan kişi

1. Maven sürümünü güncelleyin. `plugin.yml`, sürümü `${project.version}` üzerinden alır.
2. Testleri çalıştırıp gölgelenmiş JAR dosyasını derleyin.
3. GitHub'da `v5.0.1` gibi sayısal sürüm içeren bir Release hazırlayın.
4. **Derlenmiş eklentiyi** `ZonelyCore-5.0.1.jar` veya `ZonelyCore.jar` adıyla Release Assets bölümüne ekleyin.
5. Güncelleme notlarını ekleyin. JAR yüklemesi tamamlandıktan sonra yayını kararlı sürüm olarak yayımlayın ve Latest olarak seçin.

Taslak ve prerelease kayıtları kurulmaz. Yalnızca kaynak kod ZIP/TAR dosyası veya Git etiketi oluşturmak yeterli değildir. GitHub deposuna commit atılması mevcut sunuculara JAR göndermez. Bir sürüm etiketiyle JAR'ın iç sürümü eşleşmelidir.

Mevcut eski `Update` etiketi için sürüm, `ZonelyCore-1.4.jar` gibi dosya adından okunabilir. Yeni yayınlarda `vMAJOR.MINOR.PATCH` biçimini kullanın. Düşük veya aynı sürüm indirilmez.
