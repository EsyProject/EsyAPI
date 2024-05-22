package apiBoschEsy.apiInSpringBoot.service.ticket;

import apiBoschEsy.apiInSpringBoot.dto.auth.DataAuth;
import apiBoschEsy.apiInSpringBoot.dto.ticket.*;
import apiBoschEsy.apiInSpringBoot.entity.Event;
import apiBoschEsy.apiInSpringBoot.entity.Ticket;
import apiBoschEsy.apiInSpringBoot.infra.error.exceptions.*;
import apiBoschEsy.apiInSpringBoot.repository.IRepositoryEvent;
import apiBoschEsy.apiInSpringBoot.repository.IRepositoryTicket;
import apiBoschEsy.apiInSpringBoot.service.image.ImageService;
import apiBoschEsy.apiInSpringBoot.service.utils.FormatService;
import apiBoschEsy.apiInSpringBoot.service.utils.GenerateNumberQRCode;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.xml.crypto.Data;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Service
public class TicketService {

    // Access the database
    @Autowired
    private IRepositoryTicket repositoryTicket;
    @Autowired
    private FormatService formatService;
    @Autowired
    private GenerateNumberQRCode qrCodeService;
    @Autowired
    private IRepositoryEvent repositoryEvent;
    @Autowired
    private ImageService imageService;

    // Methods Controller

    // POST Register Ticket (ADMIN)
    @Transactional
    public DataDeitalRegisterTicket createTicketEventMain(@RequestBody @Valid DataRegisterTicket dataRegisterTicket, @PathVariable Long event_id, @AuthenticationPrincipal Jwt jwt) throws ExceptionDateInvalid, EventNotFoundException, NeedSamePeopleForCreate, OnlyOneTicket {
        // Date and Time
        LocalDate dateCurrent = formatService.getCurrentDate();
        String timeCurrent = formatService.getCurrentTimeFormatted();

        // Find Event by ID
        Event event = repositoryEvent.findById(event_id).orElseThrow(() -> new EventNotFoundException("Event Not found with ID: " + event_id));

        // Creating a user
        String username = new DataAuth(jwt).userName();

        // Create the ticket
        if(!(event.getAuthor().equals(username))){
            throw new NeedSamePeopleForCreate("You don't created this event, so you cannot create ticket for event!");
        }
        // Check if a ticket already exists for this event
        if (event.getTickets() != null && !event.getTickets().isEmpty()) {
            throw new OnlyOneTicket("A ticket for this event already exists. Only one ticket can be created per event.");
        }
        Ticket ticket = new Ticket(dataRegisterTicket);
        ticket.setTicket_id(1L);
        if (!(ticket.getInitialDateTicket().isAfter(dateCurrent) || ticket.getInitialDateTicket().equals(dateCurrent))) {
            throw new ExceptionDateInvalid("Invalid date! You entered a date that has already passed. Enter a future or current date!");
        }

        // Set ticket details
        ticket.setDate_created(dateCurrent);
        ticket.setTime_create(LocalTime.parse(timeCurrent));
        ticket.setEvent(event);


        // Save the ticket
        repositoryTicket.save(ticket);

        return new DataDeitalRegisterTicket(
                formatService.formattedDate(ticket.getInitialDateTicket()),
                formatService.formattedDate(ticket.getFinishDateTicket()),
                ticket.getInitialTimeTicket(),
                ticket.getFinishTimeTicket()
        );
    }

    // POST (get ticket event (user default))
    @Transactional
    @Deprecated
    public DataDeitalTicket createTicket(@PathVariable Long event_id, @AuthenticationPrincipal Jwt jwt) throws ExceptionDateInvalid, EventNotFoundException, CreateMoreTicketException, UserDontCreateTicket {

        // Date and Time
        LocalDate dateCurrent = formatService.getCurrentDate();
        String timeCurrent = formatService.getCurrentTimeFormatted();

        // Find Event by ID
        Event event = repositoryEvent.findById(event_id).orElseThrow(() -> new EventNotFoundException("Event Not found with ID: " + event_id));

        // Creating a user
        String username = new DataAuth(jwt).userName();

        // Check if user already created a ticket
        boolean userHasTicket = event.getTickets().stream().anyMatch(ticket -> ticket.getAuthor().equals(username));
        if (userHasTicket) {
            throw new CreateMoreTicketException("User has already created a ticket.");
        }

        // Creating a number for QRCode
        var qrCode = qrCodeService.generateRandomNumbers(7);

        // Create new ticket
        var newTicket = new Ticket();
        newTicket.setDate_created(dateCurrent);
        newTicket.setTime_create(LocalTime.now());
        newTicket.setQrCodeNumber(qrCode);
        newTicket.setAuthor(username);

        // Set ticket ID sequentially (if not handled by the database)
        newTicket.setTicket_id((long) (event.getTickets().size() + 1));

        // Add ticket to event and save
        event.getTickets().add(newTicket);
        repositoryTicket.save(newTicket);

        return new DataDeitalTicket(
                newTicket.getTicket_id(),
                event.getNameOfEvent(),
                newTicket.getQrCodeNumber(),
                username,
                newTicket.getDate_created(),
                timeCurrent
        );
    }


    @Transactional
    public DataDeitalUpdateTicket imageTicket(@ModelAttribute DataImageTicket dataImageTicket, @PathVariable Long event_id, @PathVariable Long ticket_id) throws TicketNotFoundException, EventNotFoundException {
        // Find event by ID
        Event event = repositoryEvent.findById(event_id).orElseThrow(() -> new EventNotFoundException("Event not found with ID: " + event_id));

        // Find ticket by ID
        Ticket ticket = event.getTickets().stream()
                .filter(t -> t.getTicket_id().equals(ticket_id))
                .findFirst()
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found with ID: " + ticket_id));

        List<MultipartFile> images = dataImageTicket.images();
        if (!(images == null) && !images.isEmpty()) {
            List<String> imageUrl = imageService.saveImagesTickets(ticket, images);
            ticket.setImageUrl(imageUrl);
        }

        return new DataDeitalUpdateTicket(ticket);
    }

    @Transactional
    public DataConfirmTicket confirmTicket(@PathVariable Long event_id, @PathVariable Long ticket_id) {
        // Find event
        var event = repositoryEvent.findById(event_id);

        if (event.isPresent()) {
            // Find ticket within the event
            var ticket = event.get().getTickets().stream()
                    .filter(t -> t.getTicket_id().equals(ticket_id))
                    .findFirst();

            if (ticket.isPresent()) {
                // Set isPresence attribute to true
                ticket.get().setIsPresence(true);

                // Save the updated ticket
                repositoryTicket.save(ticket.get());

                // Return the updated ticket
                return new DataConfirmTicket(ticket.get(), formatService.formattedDate(ticket.get().getDate_created()));
            }
        }
        // Return null if event or ticket is not found
        return null;
    }
}

