package com.anpi.app.config;

import org.apache.commons.lang.StringUtils;
import org.jsoup.helper.StringUtil;

import com.google.common.base.Strings;

public class Main {
	
	public static void main(String[] args) {
		String line = null;
		System.out.println("1:"+Strings.isNullOrEmpty(line));
		line = "null";
		System.out.println("2:"+Strings.isNullOrEmpty(line));
		line = "123";
		System.out.println("3:"+Strings.isNullOrEmpty(line));
		line = "";
		System.out.println("4:"+Strings.isNullOrEmpty(line));

		System.out.println(StringUtils.isNotBlank(line));
	}

}
