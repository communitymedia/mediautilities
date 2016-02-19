/*
 * @(#)VideoSampleDescriptionAtom.java
 *
 * $Date: 2012-01-18 09:21:11 +0000 (Wed, 18 Jan 2012) $
 *
 * Copyright (c) 2011 by Jeremy Wood.
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

/**
 * This is not a public class because I expect to make some significant changes to this project in the next year.
 * <P>
 * Use at your own risk. This class (and its package) may change in future releases.
 * <P>
 * Not that I'm promising there will be future releases. There may not be. :)
 */
class VideoSampleDescriptionAtom extends SampleDescriptionAtom {

	public VideoSampleDescriptionAtom() {
		super();
	}

	public VideoSampleDescriptionAtom(Atom parent, InputStream in) throws IOException {
		super(parent, in);
	}

	@Override
	protected SampleDescriptionEntry readEntry(InputStream in) throws IOException {
		return new VideoSampleDescriptionEntry(in);
	}
}
