\# Seat Reservation at Scale



A production-oriented seat reservation backend built using Java 17, Spring Boot, PostgreSQL and Docker.



The system is designed to handle concurrent seat reservation requests while guaranteeing:



\- No double booking

\- Exactly one successful reservation for a hot seat under concurrency

\- Per-user seat limits

\- Idempotent reservation requests

\- All-or-nothing multi-seat reservations

\- Transactional consistency

\- JWT-based authentication and role-based authorization

\- Health and readiness checks

\- Prometheus metrics

\- Structured logs with request correlation IDs

\- Dockerized deployment



\---



\## 1. Tech Stack



\- Java 17

\- Spring Boot 4.1.1

\- Spring Web MVC

\- Spring Data JPA / Hibernate

\- Spring Security 6

\- JWT

\- PostgreSQL 16

\- Maven

\- Docker

\- Docker Compose

\- Micrometer

\- Prometheus

\- Python + aiohttp for concurrency testing



\---



\## 2. Architecture



The application follows a layered Spring Boot architecture.



```text

Client

&#x20; |

&#x20; v

Spring Boot REST API

&#x20; |

&#x20; +----------------------+

&#x20; |                      |

&#x20; v                      v

Security Layer       Controllers

JWT Authentication            |

&#x20;                        v

&#x20;                    Services

&#x20;                        |

&#x20;                        v

&#x20;                  JPA Repositories

&#x20;                        |

&#x20;                        v

&#x20;                   PostgreSQL

