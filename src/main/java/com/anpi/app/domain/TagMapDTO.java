package com.anpi.app.domain;

import java.io.Serializable;

/**
 * Represents a TagMapDTO, providing access to tagValue and source
 */
public class TagMapDTO implements Serializable{

	private static final long serialVersionUID = 1L;
	private String tagValue;
	private Integer source;
	
	/**
	 * Gets the tag value.
	 *
	 * @return the tag value
	 */
	public String getTagValue() {
		return tagValue;
	}
	
	/**
	 * Sets the tag value.
	 *
	 * @param tagValue the new tag value
	 */
	public void setTagValue(String tagValue) {
		this.tagValue = tagValue;
	}
	
	/**
	 * Gets the source.
	 *
	 * @return the source
	 */
	public Integer getSource() {
		return source;
	}
	
	/**
	 * Sets the source.
	 *
	 * @param source the new source
	 */
	public void setSource(Integer source) {
		this.source = source;
	}

	@Override
	public String toString() {
		return "TagMapDTO [tagValue=" + tagValue + ", source=" + source + "]";
	}
	
	
	
	
	
}
