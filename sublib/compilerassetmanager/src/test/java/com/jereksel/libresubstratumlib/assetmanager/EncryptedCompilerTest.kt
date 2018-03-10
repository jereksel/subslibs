package com.jereksel.libresubstratumlib.assetmanager

import android.annotation.TargetApi
import android.app.Activity
import android.content.res.AssetManager
import android.os.Build
import com.jereksel.compilerassetmanager.BuildConfig
import com.jereksel.libresubstratumlib.*
import com.jereksel.libresubstratumlib.colorreader.Color
import com.jereksel.libresubstratumlib.colorreader.ColorReader
import com.jereksel.libresubstratumlib.compilercommon.ThemeToCompile
import com.jereksel.libresubstratumlib.compilercommon.Type1DataToCompile
import org.assertj.core.api.Assertions.assertThat
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

@TargetApi(Build.VERSION_CODES.O)
@Suppress("IllegalIdentifier")
@RunWith(RobolectricTestRunner::class)
@Config(constants = BuildConfig::class,
        sdk = [Build.VERSION_CODES.LOLLIPOP]
)
class EncryptedCompilerTest {

    lateinit var assetManager: AssetManager
    val aapt = TestAaptFactory.get()

    val aaptLocation = File("${System.getenv("ANDROID_HOME")}${File.separator}build-tools${File.separator}26.0.2${File.separator}aapt")

    lateinit var transformer: (InputStream) -> (InputStream)

    val temp = Files.createTempDirectory(null).toFile()

    @Before
    fun setup() {
        assetManager = Robolectric.buildActivity(Activity::class.java).create().get().assets

        val key = ByteArray(16, { it.toByte() })
        val iv = ByteArray(16, { it.toByte() })


        transformer = {
            val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    SecretKeySpec(key.clone(), "AES"),
                    IvParameterSpec(iv.clone())
            )
            CipherInputStream(it, cipher)
        }
    }

    @After
    fun cleanup() {
//        temp.deleteRecursively()
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/basicTheme")
    fun `Compiled theme should have all colors`() {
        val apk = compile("android")

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0xffabcdef"), Color("color2", "0x12345678")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type1Theme")
    fun `Type1 xmls should be replaced`() {

        val type1s = listOf(Type1DataToCompile(Type1Extension("ATYPE1", false), "a"))
        val apk = compile("android", type1s)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0x12345678"), Color("color2", "0x00000000"), Color("color3", "0x00000000")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type1Theme")
    fun `Type1 multiple type xmls should be replaced`() {

        val type1s = listOf(Type1DataToCompile(Type1Extension("BTYPE1", false), "b"), Type1DataToCompile(Type1Extension("CTYPE2", false), "c"))
        val apk = compile("android", type1s)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0x00000000"), Color("color2", "0x12345678"), Color("color3", "0xffffffff")), getColorsValues(apk).toList())
    }


    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type2Theme")
    fun `Type2 with default value compilation test`() {

        val type2 = Type2Extension("Default", true)
        val apk = compile("android", type2 = type2)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0x00000000"), Color("color2", "0x00abcdef")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type2Theme")
    fun `Type2 with non default value compilation test`() {

        val type2 = Type2Extension("test", false)
        val apk = compile("android", type2 = type2)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0xffffffff"), Color("color2", "0x00abcdef")), getColorsValues(apk).toList())
    }


    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type3Theme")
    fun `Type3 with default value compilation test`() {

        val type3 = Type3Extension("Default", true)
        val apk = compile("android", type3 = type3)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0x00000000"), Color("color2", "0x00abcdef")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type3Theme")
    fun `Type3 with non default value compilation test`() {

        val type3 = Type3Extension("test", false)
        val apk = compile("android", type3 = type3)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0xffffffff")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type3Theme")
    fun `Type3 non-default without res`() {

        val type3 = Type3Extension("no_res", false)
        val apk = compile("android", type3 = type3)

        Assert.assertTrue(apk.exists())
        Assert.assertTrue(apk.isFile)

        Assert.assertEquals(listOf(Color("color1", "0x00000000")), getColorsValues(apk).toList())
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type3WithCommonTheme")
    fun `Type3 default doesn't use common`() {

        val type3 = Type3Extension("Default", true)
        val apk = compile("android", type3 = type3)

        assertThat(apk.exists()).isTrue()
        assertThat(apk.isFile).isTrue()

        assertThat(getColorsValues(apk).toList())
                .containsExactlyInAnyOrder(Color("color1", "0x00000000"), Color("color2", "0x00abcdef"))
    }

    @Test
    @Config(assetDir = "../../src/test/resources/assetsEncrypted/type3WithCommonTheme")
    fun `Type3 non-default uses common`() {

        val type3 = Type3Extension("test", false)
        val apk = compile("android", type3 = type3)

        assertThat(apk.exists()).isTrue()
        assertThat(apk.isFile).isTrue()

        assertThat(getColorsValues(apk).toList())
                .containsExactlyInAnyOrder(Color("common_color1", "0xaaaaaaaa"), Color("color1", "0xffffffff"))

    }

    fun getColorsValues(apk: File): List<Color> {
        return ColorReader.getColorsValues(aaptLocation, apk)
    }

    fun compile(
            app: String,
            type1Data: List<Type1DataToCompile> = listOf(),
            type2: Type2Extension? = null,
            type3: Type3Extension? = null
    ) = aapt.compileTheme(assetManager, ThemeToCompile("com.app.app", "com.app.app", app, "com.app.app", type1Data, type2, type3, 0, ""), temp, listOf(), transformer)

}