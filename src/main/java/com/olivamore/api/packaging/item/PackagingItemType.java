package com.olivamore.api.packaging.item;

import com.olivamore.api.shared.persistence.ValueEnum;

public enum PackagingItemType implements ValueEnum {

	BOTTLE,
	CAP,
	LABEL;

	@Override
	public String value() {
		return name();
	}

}
