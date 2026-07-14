UPDATE users
SET password = '$2a$12$JqS.k39PeP24cJH55ZnAHOz9gwCE3KHKuRm.R08ynJjRVjpBKSyCK'
WHERE email IN ('user@example.com', 'manager@example.com', 'admin@example.com');
