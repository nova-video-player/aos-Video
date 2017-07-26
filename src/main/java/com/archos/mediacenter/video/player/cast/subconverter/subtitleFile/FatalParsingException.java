// Copyright 2017 Archos SA
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.archos.mediacenter.video.player.cast.subconverter.subtitleFile;

/**
 * This class represents problems that may arise during the parsing of a subttile file.
 * 
 * @author J. David
 *
 */
public class FatalParsingException extends Exception {

	private static final long serialVersionUID = 6798827566637277804L;
	
	private String parsingErrror;
	
	public FatalParsingException(String parsingError){
		super(parsingError);
		this.parsingErrror = parsingError;
	}
	
	@Override
	public String getLocalizedMessage(){
		return parsingErrror;
	}
	
}
