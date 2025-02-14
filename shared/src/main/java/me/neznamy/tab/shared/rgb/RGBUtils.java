package me.neznamy.tab.shared.rgb;

import java.util.HashSet;
import java.util.Set;

import me.neznamy.tab.shared.TAB;
import me.neznamy.tab.shared.packets.EnumChatFormat;
import me.neznamy.tab.shared.rgb.format.BukkitFormat;
import me.neznamy.tab.shared.rgb.format.CMIFormat;
import me.neznamy.tab.shared.rgb.format.HtmlFormat;
import me.neznamy.tab.shared.rgb.format.RGBFormatter;
import me.neznamy.tab.shared.rgb.format.UnnamedFormat1;
import me.neznamy.tab.shared.rgb.gradient.CMIGradient;
import me.neznamy.tab.shared.rgb.gradient.GradientPattern;
import me.neznamy.tab.shared.rgb.gradient.HtmlGradient;
import me.neznamy.tab.shared.rgb.gradient.IridescentGradient;
import me.neznamy.tab.shared.rgb.gradient.KyoriGradient;

/**
 * A helper class to reformat all RGB formats into the default #RRGGBB and apply gradients
 */
public class RGBUtils {

	//instance of class
	private static RGBUtils instance = new RGBUtils();
	
	//list of rgb formatters
	private Set<RGBFormatter> formats = new HashSet<RGBFormatter>();
	
	//list of gradient patterns
	private Set<GradientPattern> gradients = new HashSet<GradientPattern>();

	public RGBUtils() {
		registerRGBFormatter(new BukkitFormat());
		registerRGBFormatter(new CMIFormat());
		registerRGBFormatter(new UnnamedFormat1());
		registerRGBFormatter(new HtmlFormat());
		
		registerGradient(new CMIGradient());
		registerGradient(new HtmlGradient());
		registerGradient(new IridescentGradient());
		registerGradient(new KyoriGradient());
	}
	
	/**
	 * Returns instance of this class
	 * @return instance
	 */
	public static RGBUtils getInstance() {
		return instance;
	}
	
	/**
	 * Applies all RGB formats and gradients to text and returns it
	 * @param text - original text
	 * @return text where everything is converted to #RRGGBB
	 */
	public String applyFormats(String text, boolean ignorePlaceholders) {
		String replaced = text;
		for (RGBFormatter formatter : formats) {
			replaced = formatter.reformat(replaced);
		}
		for (GradientPattern pattern : gradients) {
			replaced = pattern.applyPattern(replaced, ignorePlaceholders);
		}
		return replaced;
	}
	
	/**
	 * Registers RGB formatter
	 * @param formatter - formatter to register
	 */
	public void registerRGBFormatter(RGBFormatter formatter) {
		formats.add(formatter);
	}
	
	/**
	 * Registers gradient pattern
	 * @param pattern - gradient pattern to register
	 */
	public void registerGradient(GradientPattern pattern) {
		gradients.add(pattern);
	}
	
	/**
	 * Converts all hex codes in given string to legacy codes
	 * @param text - text to translate
	 * @return - translated text
	 */
	public String convertRGBtoLegacy(String text) {
		if (text == null) return null;
		if (!text.contains("#")) return TAB.getInstance().getPlaceholderManager().color(text);
		String applied = applyFormats(text, false);
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < applied.length(); i++){
			char c = applied.charAt(i);
			if (c == '#') {
				try {
					if (containsLegacyCode(applied, i)) {
						sb.append(new TextColor(applied.substring(i, i+7), EnumChatFormat.getByChar(applied.charAt(i+8))).getLegacyColor().getFormat());
						i += 8;
					} else {
						sb.append(new TextColor(applied.substring(i, i+7)).getLegacyColor().getFormat());
						i += 6;
					}
				} catch (Exception e) {
					//not a valid RGB code
					sb.append(c);
				}
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}
	
	/**
	 * Returns true if text contains legacy color request at defined RGB index start
	 * @param text - text to check
	 * @param i - current index start
	 * @return true if legacy color is defined, false if not
	 */
	private static boolean containsLegacyCode(String text, int i) {
		if (text.length() - i < 9 || text.charAt(i+7) != '|') return false;
		return EnumChatFormat.getByChar(text.charAt(i+8)) != null;
	}
}