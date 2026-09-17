package io.github.sekademi.spotufi.data.lyrics

/**
 * Phonetic transliteration engine for lyrics in non-Latin scripts:
 * - Japanese (Hiragana & Katakana to Hepburn Romaji)
 * - Korean (Hangul Syllables to Revised Romanization)
 * - Devanagari (Hindi/Sanskrit to Phonetic Latin)
 *
 * Provides instant dual-line reading assistance without network requests or external APIs.
 */
object TransliterationEngine {

    private val KANA_MAP: Map<String, String> = mapOf(
        // Hiragana
        "あ" to "a", "い" to "i", "う" to "u", "え" to "e", "お" to "o",
        "か" to "ka", "き" to "ki", "く" to "ku", "け" to "ke", "こ" to "ko",
        "さ" to "sa", "し" to "shi", "す" to "su", "せ" to "se", "そ" to "so",
        "た" to "ta", "ち" to "chi", "つ" to "tsu", "て" to "te", "と" to "to",
        "な" to "na", "に" to "ni", "ぬ" to "nu", "ne" to "ne", "の" to "no",
        "は" to "ha", "ひ" to "hi", "ふ" to "fu", "へ" to "he", "ほ" to "ho",
        "ま" to "ma", "み" to "mi", "む" to "mu", "め" to "me", "も" to "mo",
        "や" to "ya", "ゆ" to "yu", "よ" to "yo",
        "ら" to "ra", "り" to "ri", "る" to "ru", "れ" to "re", "ろ" to "ro",
        "わ" to "wa", "を" to "o", "ん" to "n",
        "が" to "ga", "ぎ" to "gi", "ぐ" to "gu", "げ" to "ge", "ご" to "go",
        "ざ" to "za", "じ" to "ji", "ず" to "zu", "ぜ" to "ze", "ぞ" to "zo",
        "だ" to "da", "ぢ" to "ji", "づ" to "zu", "で" to "de", "ど" to "do",
        "ば" to "ba", "び" to "bi", "ぶ" to "bu", "べ" to "be", "ぼ" to "bo",
        "ぱ" to "pa", "ぴ" to "pi", "ぷ" to "pu", "ぺ" to "pe", "ぽ" to "po",
        "きゃ" to "kya", "きゅ" to "kyu", "きょ" to "kyo",
        "しゃ" to "sha", "しゅ" to "shu", "しょ" to "sho",
        "ちゃ" to "cha", "ちゅ" to "chu", "ちょ" to "cho",
        "にゃ" to "nya", "にゅ" to "nyu", "にょ" to "nyo",
        "ひゃ" to "hya", "ひゅ" to "hyu", "ひょ" to "hyo",
        "みゃ" to "mya", "みゅ" to "myu", "みょ" to "myo",
        "りゃ" to "rya", "りゅ" to "ryu", "りょ" to "ryo",
        "ぎゃ" to "gya", "ぎゅ" to "gyu", "ぎょ" to "gyo",
        "じゃ" to "ja", "じゅ" to "ju", "じょ" to "jo",
        "びゃ" to "bya", "びゅ" to "byu", "びょ" to "byo",
        "ぴゃ" to "pya", "ぴゅ" to "pyu", "ぴょ" to "pyo",
        // Katakana
        "ア" to "a", "イ" to "i", "ウ" to "u", "エ" to "e", "オ" to "o",
        "カ" to "ka", "キ" to "ki", "ク" to "ku", "ケ" to "ke", "コ" to "ko",
        "サ" to "sa", "シ" to "shi", "ス" to "su", "セ" to "se", "ソ" to "so",
        "タ" to "ta", "チ" to "chi", "ツ" to "tsu", "テ" to "te", "ト" to "to",
        "ナ" to "na", "ニ" to "ni", "ヌ" to "nu", "ネ" to "ne", "ノ" to "no",
        "ハ" to "ha", "ヒ" to "hi", "フ" to "fu", "ヘ" to "he", "ホ" to "ho",
        "マ" to "ma", "ミ" to "mi", "ム" to "mu", "メ" to "me", "モ" to "mo",
        "ヤ" to "ya", "ユ" to "yu", "ヨ" to "yo",
        "ラ" to "ra", "リ" to "ri", "ル" to "ru", "レ" to "re", "ロ" to "ro",
        "ワ" to "wa", "ヲ" to "o", "ン" to "n",
        "ガ" to "ga", "ギ" to "gi", "グ" to "gu", "ゲ" to "ge", "ゴ" to "go",
        "ザ" to "za", "ジ" to "ji", "ず" to "zu", "ゼ" to "ze", "ゾ" to "zo",
        "ダ" to "da", "ヂ" to "ji", "ヅ" to "zu", "デ" to "de", "ド" to "do",
        "バ" to "ba", "ビ" to "bi", "ブ" to "bu", "ベ" to "be", "ボ" to "bo",
        "パ" to "pa", "ピ" to "pi", "プ" to "pu", "ペ" to "pe", "ポ" to "po",
        "キャ" to "kya", "キュ" to "kyu", "キョ" to "kyo",
        "シャ" to "sha", "シュ" to "shu", "ショ" to "sho",
        "チャ" to "cha", "チュ" to "chu", "チョ" to "cho",
        "ニャ" to "nya", "ニュ" to "nyu", "ニョ" to "nyo",
        "ヒャ" to "hya", "ヒュ" to "hyu", "ヒョ" to "hyo",
        "ミャ" to "mya", "ミュ" to "myu", "ミョ" to "myo",
        "リャ" to "rya", "リュ" to "ryu", "リョ" to "ryo",
        "ギャ" to "gya", "ギュ" to "gyu", "ギョ" to "gyo",
        "ジャ" to "ja", "ジュ" to "ju", "ジョ" to "jo",
        "ビャ" to "bya", "ビュ" to "byu", "ビョ" to "byo",
        "ピャ" to "pya", "ピュ" to "pyu", "ピョ" to "pyo",
        "ー" to "-",
    )

    // Hangul decomposition tables (Revised Romanization of Korean)
    private val HANGUL_CHOSEONG = arrayOf(
        "g", "kk", "n", "d", "tt", "r", "m", "b", "pp",
        "s", "ss", "", "j", "jj", "ch", "k", "t", "p", "h"
    )
    private val HANGUL_JUNGSEONG = arrayOf(
        "a", "ae", "ya", "yae", "eo", "e", "yeo", "ye", "o",
        "wa", "wae", "oe", "yo", "u", "wo", "we", "wi", "yu",
        "eu", "ui", "i"
    )
    private val HANGUL_JONGSEONG = arrayOf(
        "", "k", "k", "ks", "n", "nj", "nh", "d", "l",
        "lg", "lm", "lb", "ls", "lt", "lp", "lh", "m", "b",
        "bs", "s", "ss", "ng", "j", "ch", "k", "t", "p", "h"
    )

    // Devanagari mappings
    private val DEVANAGARI_VOWELS = mapOf(
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u", 'ऊ' to "oo",
        'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ं' to "n", 'ः' to "h"
    )
    private val DEVANAGARI_MATRAS = mapOf(
        'ा' to "aa", 'ि' to "i", 'ी' to "ee", 'ु' to "u", 'ू' to "oo",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au"
    )
    private val DEVANAGARI_CONSONANTS = mapOf(
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "ny",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h"
    )

    /**
     * Checks if the text contains non-Latin scripts needing transliteration.
     */
    fun hasNonLatinScript(text: String): Boolean {
        for (ch in text) {
            val code = ch.code
            if (code in 0x3040..0x30FF ||   // Hiragana & Katakana
                code in 0xAC00..0xD7AF ||   // Hangul Syllables
                code in 0x0900..0x097F      // Devanagari
            ) {
                return true
            }
        }
        return false
    }

    /**
     * Transliterates the text into phonetic Latin characters.
     * Returns null if no non-Latin script is detected.
     */
    fun transliterate(text: String): String? {
        if (!hasNonLatinScript(text)) return null

        val sb = StringBuilder()
        var i = 0
        while (i < text.length) {
            // Check 2-character Japanese digraphs (e.g. きゃ, ぴょ)
            if (i + 1 < text.length) {
                val pair = text.substring(i, i + 2)
                val mapped = KANA_MAP[pair]
                if (mapped != null) {
                    sb.append(mapped)
                    i += 2
                    continue
                }
            }

            val ch = text[i]
            val code = ch.code

            when {
                // Japanese Kana
                KANA_MAP.containsKey(ch.toString()) -> {
                    sb.append(KANA_MAP[ch.toString()])
                }
                // Korean Hangul Syllable
                code in 0xAC00..0xD7AF -> {
                    val sIndex = code - 0xAC00
                    val choseongIndex = sIndex / (21 * 28)
                    val jungseongIndex = (sIndex % (21 * 28)) / 28
                    val jongseongIndex = sIndex % 28

                    val cho = HANGUL_CHOSEONG.getOrElse(choseongIndex) { "" }
                    val jung = HANGUL_JUNGSEONG.getOrElse(jungseongIndex) { "" }
                    val jong = HANGUL_JONGSEONG.getOrElse(jongseongIndex) { "" }
                    sb.append(cho).append(jung).append(jong)
                }
                // Devanagari Vowels
                DEVANAGARI_VOWELS.containsKey(ch) -> {
                    sb.append(DEVANAGARI_VOWELS[ch])
                }
                // Devanagari Consonants
                DEVANAGARI_CONSONANTS.containsKey(ch) -> {
                    val base = DEVANAGARI_CONSONANTS[ch]
                    // Lookahead: is next character a virama (halant '्') or matra?
                    val next = text.getOrNull(i + 1)
                    when {
                        next == '्' -> {
                            sb.append(base)
                            i++ // skip virama
                        }
                        next != null && DEVANAGARI_MATRAS.containsKey(next) -> {
                            sb.append(base).append(DEVANAGARI_MATRAS[next])
                            i++ // skip matra
                        }
                        else -> {
                            // Inherent 'a' sound for consonants
                            sb.append(base).append("a")
                        }
                    }
                }
                // Other characters (spaces, punctuation, Latin letters)
                else -> {
                    sb.append(ch)
                }
            }
            i++
        }
        return sb.toString().trim()
    }
}
