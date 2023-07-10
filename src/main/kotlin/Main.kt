import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.auth.BotAuthorization
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.BotConfiguration
import java.io.File
import java.util.*

inline fun <reified T> load(filename: String, default: () -> T): T {
    if (!File("data").exists()) {
        File("data").mkdir()
    }
    val file = File("data", filename)
    return if (file.exists()) {
        Json.decodeFromString(file.readText())
    } else {
        val r = default()
        file.writeText(Json.encodeToString(r))
        r
    }
}

@Serializable
class Lib(
    val songs: MutableList<Song>,
    val name: String
)

@Serializable
class Game(
    val group: Long,
    val songLib: MutableList<Song>,
    private val libId: Int,
    private val libName: String
) {
    val discovered: MutableList<Song> = mutableListOf()
    val tips: MutableList<Char> = mutableListOf()

    fun message(): String {
        var base = """
            猜歌曲
            范围: 曲库%LIB_ID% %LIB_NAME%
            已开字符:%TIPS%
        """.trimIndent()
        base = base.replace("%LIB_ID%", libId.toString())
        base = base.replace("%LIB_NAME%", libName)
        var tipsStr = ""
        tips.forEach {
            tipsStr += ' '
            if (it == ' ') tipsStr += "空格"
            else tipsStr += it
        }
        base = base.replace("%TIPS%", if (tipsStr!="") tipsStr else "无")
        this.songLib.forEach {
            base += "\n"
            if (discovered.contains(it)) {
                base += it.name
            } else {
                var show = ""
                it.name.forEach { char ->
                    if (tips.contains(char))
                        show += char
                    else
                        show += "*"
                }
                base += show
            }
        }
        return base
    }
}

@Serializable
class Song(val name: String)

val qqId = File("data", "username.txt").readText().toLong()
val pass = File("data", "password.txt").readText()
val mainBot = BotFactory.newBot(qqId, pass) {
    protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD
    fileBasedDeviceInfo()
}
val games = mutableListOf<Game>()
var songLibs = mutableListOf<Lib>()

suspend fun main() {
    songLibs = load("songLibs.json") {
        mutableListOf()
    }
    println(Json.encodeToString(songLibs))
    mainBot.login()
    mainBot.eventChannel.subscribeAlways<GroupMessageEvent> {
        println(message.content)
        if (message.content.startsWith("#help")) {
            group.sendMessage(
                """
                    『猜歌名游戏』帮助：
                    输入/newgame <n>使用第n个歌名库开始游戏。如果当前群聊正在进行一个游戏则会拒绝请求。
                    输入/show <c>展示对应字符。特殊地，只输入/show将会展示空格。
                    直接输入歌名将会进行检查。
                """.trimIndent()
            )
        } else if (message.content.startsWith("/poke")) {
            group.sendMessage("别在这理发店")
        } else if (message.content.startsWith("/newgame")) {
            if (!games.none { it.group == group.id }) {
                group.sendMessage("当前群聊正在进行游戏")
            } else {
                val args = message.content.substringAfter("/newgame ")
                var songLibId = args.toIntOrNull()
                val problems = (8..12).random()
                if ((songLibId == null) || (songLibId >= songLibs.size)) {
                    songLibId = (0 until songLibs.size).random()
                }
                var thisSongLib = songLibs[songLibId].songs
                thisSongLib.shuffle()
                thisSongLib = thisSongLib.slice(0 until problems step 1)
                    .toMutableList()
                val newGame = Game(
                    group.id, thisSongLib,
                    songLibId, songLibs[songLibId].name
                )
                games += newGame
//                Thread.sleep((100..200).random().toLong())
                group.sendMessage("开始游戏！")
                group.sendMessage(newGame.message())
//                Thread.sleep((100..1000).random().toLong())
                println(newGame.songLib)
            }
        } else if (message.content.startsWith("/show")) {
            val thisGame = games.filter { it.group == group.id }[0]
            val args = message.content.substringAfter("/show")
            val argChars = args.filter { it != ' ' }
            if (argChars == "") {
                thisGame.tips += ' '
            } else {
                thisGame.tips += argChars[0]
            }
//            Thread.sleep((100..1000).random().toLong())
            group.sendMessage(thisGame.message())
        } else {
            val thisGame = games.filter { it.group == group.id }[0]
            var answer: Song? = null
            thisGame.songLib.forEach {
                if (it.name.uppercase(Locale.getDefault()) ==
                    message.content.uppercase(Locale.getDefault())
                    && (!thisGame.discovered.contains(it))
                )
                    answer = it
            }
            if (answer != null) {
                thisGame.discovered += answer!!
//                Thread.sleep((100..500).random().toLong())
                group.sendMessage("\"${answer!!.name}\" 正确！")
//                Thread.sleep((100..1000).random().toLong())
                group.sendMessage(thisGame.message())
                if (thisGame.discovered.size == thisGame.songLib.size) {
                    group.sendMessage("游戏结束")
                    games.remove(thisGame)
                }
            }
        }
    }
}