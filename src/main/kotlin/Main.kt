import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import net.mamoe.mirai.BotFactory
import net.mamoe.mirai.contact.getMember
import net.mamoe.mirai.contact.isOperator
import net.mamoe.mirai.event.events.GroupMessageEvent
import net.mamoe.mirai.event.events.UserMessageEvent
import net.mamoe.mirai.message.data.at
import net.mamoe.mirai.message.data.content
import net.mamoe.mirai.utils.BotConfiguration
import java.io.File
import kotlin.math.abs

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
    val songs: MutableList<Song>, val name: String
)

@Serializable
class Game(
    val group: Long, val songLib: MutableList<Song>, val libId: Int, private val libName: String
) {
    val discovered: MutableList<Song> = mutableListOf()
    val tips: MutableList<Char> = mutableListOf()
    var he: Boolean = false
    var lastTip: Int = -1
    var launched = false
    var banned = mutableListOf<Long>()

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
        base = base.replace("%TIPS%", if (tipsStr != "") tipsStr else "无")
        this.songLib.forEach {
            base += "\n"
            if (discovered.contains(it)) {
                base += it.name
            } else {
                var specialCount = 0
                it.name.uppercase().forEach { char ->
                    if (!((char in '0'..'9') || (char in 'A'..'Z'))) {
                        specialCount += 1
                    }
                }
                specialCount /= 2
                if (he) specialCount = 0
                var show = ""
                it.name.forEach { char ->
                    if (tips.contains(char.uppercaseChar())) show += char
                    else if (!((char in '0'..'9') || (char in 'A'..'Z')) && specialCount >= 1) {
                        specialCount -= 1
                        show += char
                    } else show += "*"
                }
                base += show
            }
        }
        return base
    }
}

@Serializable
class Song(val name: String) {
    var user = 0L
}

val qqId = File("data", "username.txt").readText().toLong()
val pass = File("data", "password.txt").readText()
val mainBot = BotFactory.newBot(qqId, pass) {
    protocol = BotConfiguration.MiraiProtocol.ANDROID_PAD
    fileBasedDeviceInfo()
}
val games = mutableListOf<Game>()
var songLibs = mutableListOf<Lib>()

fun check(input: String, ans: String): Boolean {
    if (abs(input.length - ans.length) * 1.0 / ans.length >= 0.2) return false
    var sameChars = 0
    var inputUpper = input.uppercase()
    ans.forEach {
        if (inputUpper.contains(it.uppercaseChar())) {
            sameChars += 1
            val index = (inputUpper.indexOf(it.uppercaseChar()))
            inputUpper = inputUpper.substring(0, index) + inputUpper.substring(index + 1)
        }
    }
    return sameChars * 1.0 / ans.length >= 0.8
}

fun thisUserTurn(user: Long, game: Game): Boolean {
    val lastTip = game.lastTip
    val thisTip = (lastTip + 1) % game.songLib.size
    return user == game.songLib[thisTip].user
}

suspend fun main() {
    songLibs = load("songLibs.json") {
        mutableListOf()
    }
    println(Json.encodeToString(songLibs))
    mainBot.login()
    mainBot.eventChannel.subscribeAlways<GroupMessageEvent> {
        if (whiteListGroup.contains(group.id)) {
            if (message.content.startsWith("#help")) {
                group.sendMessage(
                    """
                    『猜歌名游戏』帮助：
                    输入/newgame <n>使用第n个歌名库开始游戏。如果当前群聊正在进行一个游戏则会拒绝请求。
                    输入/show <c>展示对应字符。特殊地，只输入/show将会展示空格。
                    直接输入歌名将会进行检查。
                    『开盒游戏』帮助：
                    管理员输入/kkk <n>将会结束群内的所有游戏并使用第n个歌名库开启一个开盒游戏。
                    私聊bot输入/he <群号> <name>将参加对应群内的开盒游戏，并将自己的歌曲设为name。
                    管理员输入/launch将会开启开盒游戏。
                """.trimIndent()
                )
            } else if (message.content.startsWith("/poke")) {
                group.sendMessage("别在这理发店")
            } else if (message.content.startsWith("/kkk")) {
                if (sender.permission.isOperator()) {
                    games.forEach {
                        if (it.group == group.id) {
                            games.remove(it)
                            group.sendMessage("已强制结束现有的游戏")
                        }
                    }
                    val args = message.content.substringAfter("/kkk ")
                    var songLibId = args.toIntOrNull()
                    if ((songLibId == null) || (songLibId >= songLibs.size)) {
                        songLibId = (0 until songLibs.size).random()
                    }
                    val newGame = Game(group.id, mutableListOf(), songLibId, songLibs[songLibId].name)
                    newGame.he = true
                    games += newGame
                    group.sendMessage("已创建开盒游戏")
                }
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
                    thisSongLib = thisSongLib.slice(0 until problems step 1).toMutableList()
                    val newGame = Game(
                        group.id, thisSongLib, songLibId, songLibs[songLibId].name
                    )
                    newGame.tips += ' '
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
                if (!thisGame.he || thisUserTurn(sender.id, thisGame)) {
                    if (argChars == "") {
                        thisGame.tips += ' '
                    } else {
                        if (!thisGame.tips.contains(argChars[0].uppercaseChar())) thisGame.tips += argChars[0].uppercaseChar()
                    }
                    thisGame.lastTip += 1
                }
                if (thisGame.he) {
                    group.sendMessage(
                        thisGame.message() + "\n本局由${
                            group.getMember(
                                thisGame.songLib[(thisGame.lastTip + 1) % thisGame.songLib.size].user
                            )?.at()
                        }" + "开字母"
                    )
                } else {
                    group.sendMessage(thisGame.message())
                }

//            Thread.sleep((100..1000).random().toLong())

            } else if (message.content.startsWith("/answer")) {
                val thisGame = games.filter { it.group == group.id }[0]
                println(Json.encodeToString(thisGame.songLib))
            } else if (message.content.startsWith("/launch")) {
                val thisGame = games.filter { it.group == group.id }[0]
                if (thisGame.he) {
                    thisGame.launched = true
                    thisGame.songLib.sortByDescending { group.getMember(it.user)?.joinTimestamp }
                    group.sendMessage("开盒，启动！")
                    group.sendMessage(
                        thisGame.message() + "\n本局由${
                            group.getMember(
                                thisGame.songLib[(thisGame.lastTip + 1) % thisGame.songLib.size].user
                            )?.at()
                        }" + "开字母"
                    )
                }
            } else {
                val thisGame = games.filter { it.group == group.id }.getOrNull(0)
                if (thisGame != null && !thisGame.he) {
                    var answer: Song? = null
                    thisGame.songLib.forEach {
                        if (check(message.content, it.name) && (!thisGame.discovered.contains(it))) answer = it
                    }
                    if (answer != null) {
                        thisGame.discovered += answer!!
//                Thread.sleep((100..500).random().toLong())
                        group.sendMessage("\"${answer!!.name}\" 正确！")
//                Thread.sleep((100..1000).random().toLong())
                        group.sendMessage(thisGame.message())
                        if (thisGame.discovered.size >= thisGame.songLib.size - 1) {
                            group.sendMessage("游戏结束")
                            games.remove(thisGame)
                        }
                    }
                } else if (thisGame != null) {
                    if (thisGame.songLib.any { it.user == sender.id }) {
                        var answer: Song? = null
                        thisGame.songLib.forEach {
                            if (check(message.content, it.name) && (!thisGame.discovered.contains(it))) answer = it
                        }
                        if (answer != null) {
                            val target = group.getMember(answer!!.user)
                            thisGame.discovered += answer!!
//                Thread.sleep((100..500).random().toLong())
                            group.sendMessage("\"${answer!!.name}\" 正确！")
                            group.sendMessage(sender.at() + "把" + target!!.at() + "开力（喜）")
                            target.mute(3600)
                            thisGame.banned += answer!!.user

//                Thread.sleep((100..1000).random().toLong())
                            group.sendMessage(thisGame.message())
                            if (thisGame.discovered.size == thisGame.songLib.size) {
                                group.sendMessage("游戏结束")
                                games.remove(thisGame)
                                thisGame.banned.forEach {
                                    group.getMember(it)?.unmute()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    mainBot.eventChannel.subscribeAlways<UserMessageEvent> {
        if (message.content.startsWith("/he")) {
            val args = message.content.substringAfter("/he ")
            val argsList = args.split(" ", limit = 2)
            val group = argsList[0]
            val songName = argsList[1]
            val thisGame = games.filter { it.group.toString() == group }.getOrNull(0)
            if (thisGame != null) {
                if (thisGame.launched) sender.sendMessage("开盒游戏收集阶段已经结束")
                else {
                    if (songLibs[thisGame.libId].songs.any { it.name.uppercase() == songName.uppercase() }) {

                        val newSong = Song(songName)
                        newSong.user = sender.id
                        thisGame.songLib.forEach {
                            if (it.user == sender.id) {
                                sender.sendMessage("已删除您先前选择的歌曲: ${it.name}")
                                thisGame.songLib.remove(it)
                            }
                        }
                        thisGame.songLib += newSong
                        sender.sendMessage("已添加歌曲：${newSong.name}")
                    } else {
                        sender.sendMessage("该曲目不在曲库中")
                    }
                }
            } else {
                sender.sendMessage("该群聊未在进行开盒游戏")
            }

        }
    }
}