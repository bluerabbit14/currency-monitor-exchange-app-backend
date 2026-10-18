-- Google sign-in stores the verified Google subject in user_auth_providers (provider = 'GOOGLE',
-- provider_user_id = sub). The existing unique (provider, provider_user_id) constraint already makes a Google
-- identity belong to at most one user. This index is the other half: a user has at most one Google identity,
-- so two concurrent link requests for different Google accounts cannot both succeed.
CREATE UNIQUE INDEX user_auth_providers_user_google_uq
    ON user_auth_providers (user_id)
    WHERE provider = 'GOOGLE';
