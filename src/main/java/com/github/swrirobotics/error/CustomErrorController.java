// *****************************************************************************
//
// Copyright (c) 2015, Southwest Research Institute® (SwRI®)
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//     * Redistributions of source code must retain the above copyright
//       notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above copyright
//       notice, this list of conditions and the following disclaimer in the
//       documentation and/or other materials provided with the distribution.
//     * Neither the name of Southwest Research Institute® (SwRI®) nor the
//       names of its contributors may be used to endorse or promote products
//       derived from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL Southwest Research Institute® BE LIABLE
// FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
// DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
// SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
// CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
// LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
// OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH
// DAMAGE.
//
// *****************************************************************************

package com.github.swrirobotics.error;

import com.google.common.base.Throwables;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.text.MessageFormat;

@Controller
class CustomErrorController {
	
	/**
	 * Display an error page, as defined in web.xml <code>custom-error</code> element.
	 */
	@RequestMapping("generalError")	
	public String generalError(HttpServletRequest request, HttpServletResponse response, Model model) {
		// retrieve some useful information from the request
		Integer statusCode = (Integer) request.getAttribute("javax.servlet.error.status_code");
		Throwable throwable = (Throwable) request.getAttribute("javax.servlet.error.exception");
		// String servletName = (String) request.getAttribute("javax.servlet.error.servlet_name");
		String exceptionMessage = getExceptionMessage(throwable, statusCode);
		
		String requestUri = (String) request.getAttribute("javax.servlet.error.request_uri");
		if (requestUri == null) {
			requestUri = "Unknown";
		}
		
		String message = MessageFormat.format("{0} returned for {1} with message {2}", 
			statusCode, requestUri, exceptionMessage
		); 
		
		model.addAttribute("errorMessage", message);
        return "error/general";
	}

	private String getExceptionMessage(Throwable throwable, Integer statusCode) {
		if (throwable != null) {
			return Throwables.getRootCause(throwable).getMessage();
		}
		HttpStatus httpStatus = HttpStatus.valueOf(statusCode);
		return httpStatus.getReasonPhrase();
	}
}
