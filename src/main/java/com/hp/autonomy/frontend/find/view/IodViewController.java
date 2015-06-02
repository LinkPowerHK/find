package com.hp.autonomy.frontend.find.view;

import com.hp.autonomy.iod.client.error.IodErrorException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;

@Controller
@RequestMapping({"/api/user/view"})
@Slf4j
public class IodViewController extends AbstractViewController {

    @Autowired
    private IodViewService iodViewService;

    @Autowired
    private MessageSource messageSource;

    @RequestMapping(value = "/viewDocument", method = RequestMethod.GET)
    public void viewDocument(
            @RequestParam("reference") final String reference,
            @RequestParam("indexes") final String indexes,
            final HttpServletResponse response
    ) throws IodErrorException, IOException {
        response.setContentType(MediaType.TEXT_HTML_VALUE);
        iodViewService.viewDocument(response.getOutputStream(), reference, indexes);
    }

    @ExceptionHandler
    public ModelAndView handleIodErrorException(
            final IodErrorException e,
            final HttpServletRequest request,
            final HttpServletResponse response
    ) {
        response.reset();

        log.error("IodErrorException thrown while viewing document", e);

        final Locale locale = Locale.ENGLISH;

        final String errorKey = "error.iodErrorCode." + e.getErrorCode();
        String iodErrorMessage;

        try {
            iodErrorMessage = messageSource.getMessage(errorKey, null, locale);
        } catch (final NoSuchMessageException e1) {
            // we don't have a key in the bundle for this error code
            iodErrorMessage = messageSource.getMessage("error.unknownError", null, locale);
        }

        final int errorCode;

        if (e.isServerError()) {
            errorCode = 500;
        } else {
            errorCode = 400;
        }

        final String subMessage;

        if (iodErrorMessage != null) {
            subMessage = messageSource.getMessage("error.iodErrorSub", new String[]{iodErrorMessage}, locale);
        } else {
            subMessage = messageSource.getMessage("error.iodErrorSubNull", null, locale);
        }

        response.setStatus(errorCode);

        return buildErrorModelAndView(
                request,
                messageSource.getMessage("error.iodErrorMain", null, locale),
                subMessage
        );
    }
}