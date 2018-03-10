package com.jereksel.libresubstratumlib.assetmanager

import android.content.res.AssetManager
import com.jereksel.libresubstratumlib.compilercommon.AndroidManifestGenerator
import com.jereksel.libresubstratumlib.compilercommon.InvalidInvocationException
import com.jereksel.libresubstratumlib.compilercommon.ThemeToCompile
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream

class AaptCompiler(
        private val aaptPath: String,
        testing: Boolean = false
) {

    private val logger = LoggerFactory.getLogger(javaClass.name)

    private val generator = AndroidManifestGenerator(testing)

    fun compileTheme(assetManager: AssetManager, themeDate: ThemeToCompile, tempDir: File,
                     additionalApks: List<String> = listOf(), transform: (InputStream) -> InputStream = { it }): File {

        val apkLocation = File(tempDir, "overlay.apk")
        val compilationDir = File(tempDir, "compilation")
        compilationDir.mkdirs()

        val manifest = generator.generateManifest(themeDate)
        val manifestFile = File(tempDir, "AndroidManifest.xml")

        manifestFile.createNewFile()
        manifestFile.writeText(manifest)

        val command = mutableListOf(aaptPath, "package", "--auto-add-overlay", "-f", "-M", manifestFile.absolutePath, "-F", apkLocation.absolutePath)

        val amLoc = "overlays/${themeDate.originalTargetApp}"

        val list = assetManager.list(amLoc).toSet()

        val mainRes = File(compilationDir, "res")

        val type3CommonLocation = "$amLoc/type3-common"

        val type3 = themeDate.type3

        if (type3 != null && !type3.default) {
            val amLocation = "$amLoc/type3_${type3.name}"

            if (assetManager.list(type3CommonLocation).isNotEmpty()) {
                if (assetManager.list(type3CommonLocation).contains("res")) {
                    assetManager.extract("$type3CommonLocation/res", mainRes, transform)
                } else {
                    assetManager.extract(type3CommonLocation, mainRes, transform)
                }
            }

            if (assetManager.list(amLocation).contains("res")) {
                //Some themes have "res/" in type3
                assetManager.extract("$amLocation/res", mainRes, transform)
            } else {
                assetManager.extract(amLocation, mainRes, transform)
            }

        } else {
            if (list.contains("res")) {
                mainRes.mkdirs()
                assetManager.extract("$amLoc/res", mainRes, transform)
            }
        }

        themeDate.type1
                .filterNot { it.extension.default }
                .forEach {
                    val amLocation = "$amLoc/type1${it.suffix}_${it.extension.name}.xml"
                    val amLocationEnc = "$amLoc/type1${it.suffix}_${it.extension.name}.xml.enc"
                    val source = File(tempDir, "type1${it.suffix}_${it.extension.name}.xml")
                    assetManager.extract(amLocation, source, transform)
                    assetManager.extract(amLocationEnc, source, transform)
                    val dest = File(mainRes, "values", "type1${it.suffix}.xml")
                    source.copyTo(dest, overwrite = true)
                }

        additionalApks.forEach {
            command.addAll(listOf("-I", it))
        }

        val type2 = themeDate.type2

        if (type2 != null && !type2.default) {
            val file = File(tempDir, "type2")
            val amLocation = "$amLoc/type2_${type2.name}"
            assetManager.extract(amLocation, file, transform)
            if (File(file, "res").exists()) {
                command.addAll(listOf("-S", File(file, "res").absolutePath))
            } else if (file.exists()) {
                command.addAll(listOf("-S", file.absolutePath))
            }
        }

        if (mainRes.exists() && mainRes.list().isNotEmpty()) {
            command.addAll(listOf("-S", mainRes.absolutePath))
        }

        logger.debug("Invoking: {}", command.joinToString(separator = " "))

        logger.debug("AndroidManifest:\n{}", manifest)

        val proc = ProcessBuilder(command)
                .start()

        val statusCode = proc.waitFor()

        val output = proc.inputStream.bufferedReader().use { it.readText() }
        val error = proc.errorStream.bufferedReader().use { it.readText() }

        if (statusCode != 0) {
            throw InvalidInvocationException(error)
        }

        return apkLocation
    }

    private fun AssetManager.extract(location: String, dest: File, transform: (InputStream) -> (InputStream)) {

        val children = this.list(location)

        if (children.isEmpty()) {
            //This is file
            dest.parentFile.mkdirs()

            val `is`: InputStream

            try {
                `is` = open(location)
            } catch (e: FileNotFoundException) {
                return
            }

            if (dest.exists()) {
                dest.delete()
            }

            dest.createNewFile()

            `is`.use { inputStream ->
                dest.outputStream().use { fileOutputStream ->
                    transform(inputStream).copyTo(fileOutputStream)
                }
            }

            if (dest.name.endsWith(".enc")) {
                val newFile = File(dest.absolutePath.removeSuffix(".enc"))
                if (newFile.exists()) {
                    newFile.delete()
                }
                dest.renameTo(newFile)
            }

        } else {
            children.forEach { extract("$location/$it", File(dest, it), transform) }
        }

    }

    fun File(file: File, vararg subDirs: String) = subDirs.fold(file) { total, next -> java.io.File(total, next) }

    private fun AssetManager.fileExists(location: String) =
            try {
                this.open(location).use {  }
                true
            } catch (e: FileNotFoundException) {
                false
            }

}
