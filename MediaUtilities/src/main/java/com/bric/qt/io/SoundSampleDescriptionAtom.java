/*
 * @(#)SoundSampleDescriptionAtom.java
 *
 * $Date: 2012-03-14 17:27:16 +0000 (Wed, 14 Mar 2012) $
 *
 * Copyright (c) 2012 by Jeremy Wood.
 * All rights reserved.
 *
 * The copyright of this software is owned by Jeremy Wood. 
 * You may not use, copy or modify this software, except in  
 * accordance with the license agreement you entered into with  
 * Jeremy Wood. For details see accompanying license terms.
 * 
 * This software is probably, but not necessarily, discussed here:
 * http://javagraphics.java.net/
 * 
 * That site should also contain the most recent official version
 * of this software.  (See the SVN repository for more details.)
 */
package com.bric.qt.io;

import java.io.IOException;
import java.io.InputStream;

public class SoundSampleDescriptionAtom extends SampleDescriptionAtom {

	public SoundSampleDescriptionAtom() {
		super();
	}

	public SoundSampleDescriptionAtom(Atom parent, InputStream in) throws IOException {
		super(parent, in);
	}

	@Override
	protected SampleDescriptionEntry readEntry(InputStream in) throws IOException {
		return new SoundSampleDescriptionEntry(in);
	}
}
