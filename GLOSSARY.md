# User Auth

Registers users and proves who they are: it issues credentials at login and tells other services whether a credential is still good.

## Language

**Session**:
One authenticated login, proven by exactly one Token. It ends at logout, at expiry, or when the user's password is reset.
_Avoid_: Login, token (as a synonym)

**Role**:
A named permission level held by a user. `ROLE_USER` is granted to every user at signup; `ROLE_ADMIN` is reserved and not yet assignable.
_Avoid_: Permission, authority

**Token**:
The JWT credential a client presents to prove a Session. A Token identifies exactly one Session.
_Avoid_: Session, JWT (in domain discussion)
