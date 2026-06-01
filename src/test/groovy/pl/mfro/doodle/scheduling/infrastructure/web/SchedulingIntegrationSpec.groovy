package pl.mfro.doodle.scheduling.infrastructure.web

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import pl.mfro.doodle.TestcontainersConfiguration
import pl.mfro.doodle.scheduling.infrastructure.database.MeetingJpaRepository
import pl.mfro.doodle.scheduling.infrastructure.database.TimeSlotEntity
import pl.mfro.doodle.scheduling.infrastructure.database.TimeSlotJpaRepository
import spock.lang.Specification
import tools.jackson.databind.ObjectMapper

import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import static org.hamcrest.Matchers.hasSize
import static org.hamcrest.Matchers.is
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration)
class SchedulingIntegrationSpec extends Specification {

  @Autowired
  private MockMvc mockMvc

  @Autowired
  private TimeSlotJpaRepository timeSlotRepository

  @Autowired
  private MeetingJpaRepository meetingRepository

  @Autowired
  private ObjectMapper objectMapper

  private UUID userId = UUID.fromString("44c4b69c-29b1-4b3d-9d7a-115df6000001")

  def setup() {
    meetingRepository.deleteAll()
    timeSlotRepository.deleteAll()
  }

  def "should successfully create a new free timeslot when no overlap exists"() {
    given: "a valid request for a future timeslot"
    def request = new SlotCreationRequest(
            Instant.parse("2026-06-10T12:00:00Z"),
            Instant.parse("2026-06-10T13:00:00Z")
    )

    expect: "the request to succeed with HTTP 201 Created and return correct body"
    mockMvc.perform(post("/api/v1/slots")
            .header("X-User-Id", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath('$.userId', is(userId.toString())))
            .andExpect(jsonPath('$.isFree', is(true)))
  }

  def "should reject slot creation when it overlaps with an existing slot"() {
    given: "pre-existing data in the database representing an already allocated slot"
    def existingSlot = new TimeSlotEntity(
            UUID.randomUUID(),
            userId,
            Instant.parse("2026-06-10T14:00:00Z").atZone(ZoneOffset.UTC),
            Instant.parse("2026-06-10T15:00:00Z").atZone(ZoneOffset.UTC),
            true
    )
    timeSlotRepository.save(existingSlot)

    and: "a new request that clashes with the existing 14:00-15:00 window"
    def clashingRequest = new SlotCreationRequest(
            Instant.parse("2026-06-10T14:30:00Z"),
            Instant.parse("2026-06-10T15:30:00Z")
    )

    expect: "the system to throw a validation exception and return HTTP 400"
    mockMvc.perform(post("/api/v1/slots")
            .header("X-User-Id", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(clashingRequest)))
            .andExpect(status().isBadRequest())
  }

  def "should aggregate and return only slots belonging to the requesting tenant within time window"() {
    given: "slots populated for two different users to verify multi-tenant data isolation"
    def otherUser = UUID.randomUUID()

    def slotUser1 = new TimeSlotEntity(UUID.randomUUID(), userId, Instant.parse("2026-06-11T09:00:00Z").atZone(ZoneOffset.UTC), Instant.parse("2026-06-11T10:00:00Z").atZone(ZoneOffset.UTC), true,)
    def slotUser2 = new TimeSlotEntity(UUID.randomUUID(), userId, Instant.parse("2026-06-11T11:00:00Z").atZone(ZoneOffset.UTC), Instant.parse("2026-06-11T12:00:00Z").atZone(ZoneOffset.UTC), true)
    def slotOther = new TimeSlotEntity(UUID.randomUUID(), otherUser, Instant.parse("2026-06-11T09:30:00Z").atZone(ZoneOffset.UTC), Instant.parse("2026-06-11T10:30:00Z").atZone(ZoneOffset.UTC), true)

    timeSlotRepository.saveAll([slotUser1, slotUser2, slotOther])

    expect: "GET query to fetch exactly 2 slots belonging exclusively to the tenant in the X-User-Id header"
    mockMvc.perform(get("/api/v1/slots")
            .header("X-User-Id", userId.toString())
            .param("from", "2026-06-11T00:00:00Z")
            .param("to", "2026-06-12T00:00:00Z"))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$', hasSize(2)))
            .andExpect(jsonPath('$.[0].userId', is(userId.toString())))
            .andExpect(jsonPath('$.[1].userId', is(userId.toString())))
  }

  def "should successfully update and delete an existing timeslot"() {
    given: "an existing free slot in the database"
    def slotId = UUID.randomUUID()
    def slot = new TimeSlotEntity(
            slotId,
            userId,
            Instant.parse("2026-06-12T10:00:00Z").atZone(ZoneOffset.UTC),
            Instant.parse("2026-06-12T11:00:00Z").atZone(ZoneOffset.UTC),
            true,
    )
    timeSlotRepository.save(slot)

    and: "an update request to shift hours and mark as busy"
    def updateRequest = new UpdateSlotRequest(
            Instant.parse("2026-06-12T11:00:00Z"),
            Instant.parse("2026-06-12T12:00:00Z"),
            false
    )

    when: "we execute the PUT request to modify the slot"
    mockMvc.perform(put("/api/v1/slots/" + slotId)
            .header("X-User-Id", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath('$.isFree', is(false)))

    then: "the slot should be updated in the repository"
    def updatedEntity = timeSlotRepository.findById(slotId).get()
    updatedEntity.startTime.toInstant() == Instant.parse("2026-06-12T11:00:00Z")
    !updatedEntity.isFree()

    when: "we execute the DELETE request to remove the slot"
    mockMvc.perform(delete("/api/v1/slots/" + slotId)
            .header("X-User-Id", userId.toString()))
            .andExpect(status().isNoContent())

    then: "the slot should no longer exist in the repository"
    !timeSlotRepository.findById(slotId).isPresent()
  }

  def "should successfully convert an available timeslot into a meeting with details and participants"() {
    given: "an available slot in the database"
    def slotId = UUID.randomUUID()
    def slot = new TimeSlotEntity(
            slotId,
            userId,
            Instant.parse("2026-06-15T09:00:00Z").atZone(ZoneOffset.UTC),
            Instant.parse("2026-06-15T10:00:00Z").atZone(ZoneOffset.UTC),
            true
    )
    timeSlotRepository.save(slot)

    and: "a request containing meeting details and participants list"
    def bookRequest = new ConvertSlotToMeetingRequest(
            "Tech Interview Discussion",
            "Reviewing the architecture and clean code challenge",
            ["tejumoluwa@doodle.com", "mateusz@doodle.com"]
    )

    expect: "the booking endpoint to accept request and return HTTP 201 Created"
    mockMvc.perform(post("/api/v1/slots/" + slotId + "/book")
            .header("X-User-Id", userId.toString())
            .contentType(MediaType.APPLICATION_JSON)
            .content(objectMapper.writeValueAsString(bookRequest)))
            .andExpect(status().isCreated())

    and: "the time slot status in the database must be automatically flipped to busy (isFree = false)"
    def closedSlot = timeSlotRepository.findById(slotId).get()
    !closedSlot.isFree()

    and: "the meeting record must be correctly stored with all nested components"
    def meetings = meetingRepository.findAll()
    meetings.size() == 1
    meetings[0].timeSlotId == slotId
    meetings[0].title == "Tech Interview Discussion"
    meetings[0].participants.size() == 2
    meetings[0].participants.contains("tejumoluwa@doodle.com")
  }

  def "should prevent race conditions and double booking using distributed lock under concurrent load"() {
    given: "an available slot in the database"
    def slotId = UUID.randomUUID()
    def slot = new TimeSlotEntity(
            slotId,
            userId,
            Instant.parse("2026-06-20T10:00:00Z").atZone(ZoneOffset.UTC),
            Instant.parse("2026-06-20T11:00:00Z").atZone(ZoneOffset.UTC),
            true
    )
    timeSlotRepository.save(slot)

    and: "a payload for booking a meeting"
    def bookRequest = new ConvertSlotToMeetingRequest(
            "Concurrent Booking Battle",
            "Testing Redisson Distributed Lock resilience",
            ["competitor@doodle.com"]
    )
    def payload = objectMapper.writeValueAsString(bookRequest)

    and: "concurrency infrastructure setup to fire multiple requests at the exact same moment"
    def numberOfThreads = 5
    def service = Executors.newFixedThreadPool(numberOfThreads)
    def startLatch = new CountDownLatch(1)
    def finishLatch = new CountDownLatch(numberOfThreads)


    def successCount = new AtomicInteger(0)
    def failureCount = new AtomicInteger(0)

    when: "5 threads concurrently attack the same slot booking endpoint"
    numberOfThreads.times {
      service.submit(() -> {
        try {
          startLatch.await()

          def result = mockMvc.perform(post("/api/v1/slots/" + slotId + "/book")
                  .header("X-User-Id", userId.toString())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(payload))
                  .andReturn()

          def status = result.getResponse().getStatus()
          if (status == 201) {
            successCount.incrementAndGet()
          } else if(status == 409){
            failureCount.incrementAndGet()
          }
        } catch (Exception e) {
        } finally {
          finishLatch.countDown()
        }
      })
    }

    startLatch.countDown()
    finishLatch.await(5, TimeUnit.SECONDS)
    service.shutdown()

    then: "EXACTLY ONE thread must successfully acquire the Redisson lock and create the meeting"
    successCount.get() == 1

    and: "all remaining threads must fail cleanly due to lock acquisition failure"
    failureCount.get() == numberOfThreads - 1

    and: "the database state must be perfectly consistent (only 1 meeting record created)"
    def meetings = meetingRepository.findAll()
    meetings.size() == 1
    meetings[0].timeSlotId == slotId
  }

}
