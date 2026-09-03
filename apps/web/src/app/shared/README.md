# Shared presentation boundary

Reusable presentation components, pipes, and display utilities belong here when
they are needed by multiple features. They must not own feature business rules,
session state, or HTTP configuration.

Task 8.1 needs no shared presentation implementation: the application shell is
under `core/layout/`, and each structural page belongs to its feature. This file
records the boundary without adding unused components or empty directories.
